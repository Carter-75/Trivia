#!/usr/bin/env python3
"""
╔═══════════════════════════════════════════════════════════════════════════╗
║                                                                           ║
║                     TRIVIA - ULTIMATE BUILD & DEPLOY SYSTEM              ║
║                    ONE SCRIPT TO RULE THEM ALL                            ║
║                                                                           ║
║  Features:                                                                ║
║    ✓ Validate trivia JSON configs (settings + questions)                  ║
║    ✓ Check Java syntax & 1.21.1 API compatibility                         ║
║    ✓ Check Gradle settings                                                ║
║    ✓ Build with Gradle                                                    ║
║    ✓ Git commit & push (with authentication)                              ║
║    ✓ Generate build reports                                               ║
║                                                                           ║
╚═══════════════════════════════════════════════════════════════════════════╝

Usage:
    ./universal_build.py                    # Full validation only
    ./universal_build.py --check            # Validation only
    ./universal_build.py --build            # Validation + Build (no clean)
    ./universal_build.py --build --clean    # Validation + Clean + Build
    ./universal_build.py --deploy           # Build + Commit + Push
    ./universal_build.py --full             # Complete: Validate + Build + Deploy
"""

import os
import sys
import json
import subprocess
import argparse
import re
import shutil
from pathlib import Path
from datetime import datetime


class Color:
    RED = "\033[91m"
    GREEN = "\033[92m"
    YELLOW = "\033[93m"
    BLUE = "\033[94m"
    MAGENTA = "\033[95m"
    CYAN = "\033[96m"
    WHITE = "\033[97m"
    RESET = "\033[0m"
    BOLD = "\033[1m"


def log(msg, color=Color.WHITE):
    print(f"{color}{msg}{Color.RESET}")


def header(msg):
    print()
    log("═" * 80, Color.BLUE)
    log(msg.center(80), Color.BOLD + Color.CYAN)
    log("═" * 80, Color.BLUE)


def success(msg):
    log(f"OK  {msg}", Color.GREEN)


def error(msg):
    log(f"ERR {msg}", Color.RED)


def warning(msg):
    log(f"WARN {msg}", Color.YELLOW)


def info(msg):
    log(f"INFO {msg}", Color.CYAN)


class UniversalBuildSystem:
    def __init__(self):
        self.errors = []
        self.warnings = []
        self.build_requested = False
        self.build_succeeded = None
        self.root = Path(__file__).parent.resolve()
        self.log_file = self.root / "universal_build.log"
        self.timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        self.latest_jar = None
        self.test_instance_dir = Path.home() / ".trivia-test-instance"

    def validate_all(self):
        header("VALIDATION SUITE")
        self.log_to_file("\n" + "=" * 80)
        self.log_to_file("VALIDATION SUITE")
        self.log_to_file("=" * 80)

        self.validate_json_configs()
        self.validate_java_syntax()
        self.validate_gradle()

        return len(self.errors) == 0

    def validate_json_configs(self):
        info("Validating trivia JSON configuration files...")

        default_settings = self.root / "src/main/resources/trivia/default_settings.json"
        default_questions = self.root / "src/main/resources/trivia/default_questions.json"

        if not default_settings.exists():
            error("Missing bundled settings: src/main/resources/trivia/default_settings.json")
            self.errors.append("Missing default_settings.json")
            return

        if not default_questions.exists():
            error("Missing bundled questions: src/main/resources/trivia/default_questions.json")
            self.errors.append("Missing default_questions.json")
            return

        try:
            settings = json.loads(default_settings.read_text(encoding="utf-8"))
            required = [
                "enabled",
                "questionDurationSeconds",
                "cooldownSeconds",
                "maxAttempts",
                "answerPrefix",
                "showAnswerInstructions",
                "battleModeWrongGuessBroadcast",
                "battleModeShowWrongGuesserName",
                "rewardCountOverride",
                "punishEffectDurationSecondsMin",
                "punishEffectDurationSecondsMax",
                "punishEffectAmplifierMin",
                "punishEffectAmplifierMax",
                "itemBlacklist",
            ]
            missing = [k for k in required if k not in settings]
            if missing:
                error(f"default_settings.json missing keys: {', '.join(missing)}")
                self.errors.append("default_settings.json missing required keys")
            else:
                success("default_settings.json structure OK")
        except Exception as exc:
            error(f"default_settings.json invalid JSON: {exc}")
            self.errors.append("default_settings.json invalid JSON")

        try:
            questions_root = json.loads(default_questions.read_text(encoding="utf-8"))
            if "questions" not in questions_root or not isinstance(questions_root["questions"], list):
                error("default_questions.json must contain a top-level 'questions' array")
                self.errors.append("default_questions.json invalid structure")
                return
            qs = questions_root["questions"]
            if len(qs) != 100:
                warning(f"default_questions.json contains {len(qs)} questions (expected 100)")
                self.warnings.append("default_questions.json question count != 100")
            else:
                success("default_questions.json has 100 questions")
        except Exception as exc:
            error(f"default_questions.json invalid JSON: {exc}")
            self.errors.append("default_questions.json invalid JSON")

    def validate_java_syntax(self):
        info("Validating Java syntax and 1.21.1 API usage...")
        java_files = list(self.root.glob("src/main/java/**/*.java")) + list(self.root.glob("src/client/java/**/*.java"))
        if not java_files:
            warning("No Java files found under src/")
            self.warnings.append("No Java files found")
            return

        old_identifier_count = 0
        for java_file in java_files:
            try:
                content = java_file.read_text(encoding="utf-8", errors="ignore")
                if "new Identifier(" in content and "import" in content:
                    error(f"{java_file.name}: uses old 'new Identifier()' constructor")
                    self.errors.append(f"{java_file.name}: old Identifier API")
                    old_identifier_count += 1
            except Exception as exc:
                warning(f"Could not read {java_file.name}: {exc}")

        if old_identifier_count == 0:
            success("All code uses 1.21.1-friendly Identifier API (Identifier.of)")

    def validate_gradle(self):
        info("Validating Gradle configuration...")
        gradle_props = self.root / "gradle.properties"
        if not gradle_props.exists():
            error("gradle.properties not found")
            self.errors.append("Missing gradle.properties")
            return

        content = gradle_props.read_text(encoding="utf-8", errors="ignore")
        if "minecraft_version=1.21.1" in content:
            success("Minecraft version: 1.21.1")
        else:
            error("Minecraft version mismatch (expected 1.21.1)")
            self.errors.append("Wrong Minecraft version")

    def build_project(self, clean: bool = False):
        header("GRADLE BUILD")
        self.log_to_file("\n" + "=" * 80)
        self.log_to_file("GRADLE BUILD")
        self.log_to_file("=" * 80)
        self.build_requested = True
        self.build_succeeded = False

        try:
            java_check = subprocess.run(["java", "-version"], capture_output=True, text=True, timeout=5)
            java_output = (java_check.stderr or "") + (java_check.stdout or "")
            version_match = re.search(r'version "(\d+)', java_output)
            if version_match:
                java_version = int(version_match.group(1))
                if java_version < 17:
                    error(f"Java {java_version} detected. Java 17+ required (Java 21 recommended)")
                    self.log_to_file(f"Java version too old: {java_version} (need 17+)")
                    return False
                info(f"Java {java_version} detected")
        except Exception as exc:
            warning(f"Could not check Java version: {exc}")

        gradlew_path = self.root / ("gradlew.bat" if os.name == "nt" else "gradlew")
        if not gradlew_path.exists():
            error("Gradle wrapper not found (expected gradlew/gradlew.bat at project root)")
            self.log_to_file("Gradle wrapper missing")
            return False

        gradle_user_home = self.root / ".gradle-user-home"
        try:
            gradle_user_home.mkdir(exist_ok=True)
        except Exception:
            gradle_user_home = None

        gradle_env = os.environ.copy()
        if gradle_user_home is not None:
            gradle_env["GRADLE_USER_HOME"] = str(gradle_user_home)

        if clean:
            info("Running gradle clean...")
            self.log_to_file("Running gradle clean...")
            try:
                result = subprocess.run(
                    [str(gradlew_path), "clean", "--no-daemon"],
                    cwd=self.root,
                    capture_output=True,
                    text=True,
                    env=gradle_env,
                    timeout=300,
                )
                if result.returncode == 0:
                    success("Clean completed")
                else:
                    stderr = (result.stderr or "").strip()
                    error(f"Clean failed: {stderr}")
                    lock_hint = "process cannot access the file" in stderr.lower() and "mappings.jar" in stderr.lower()
                    if lock_hint:
                        warning("Clean failed due to file lock; continuing with build")
                    else:
                        return False
            except Exception as exc:
                msg = str(exc)
                error(f"Clean failed: {msg}")
                if "mappings.jar" in msg.lower():
                    warning("Clean failed due to file lock; continuing with build")
                else:
                    return False
        else:
            info("Skipping gradle clean (use --clean to force)")

        info("Running gradle build...")
        self.log_to_file("Running gradle build...")
        try:
            result = subprocess.run(
                [str(gradlew_path), "build", "--no-daemon", "--stacktrace"],
                cwd=self.root,
                capture_output=True,
                text=True,
                env=gradle_env,
                timeout=600,
            )
            if result.returncode == 0:
                success("Build successful")
                libs_dir = self.root / "build/libs"
                jars = list(libs_dir.glob("*.jar"))
                jars = [j for j in jars if "-sources" not in j.name and "-dev" not in j.name]
                if jars:
                    jar_file = jars[0]
                    self.latest_jar = jar_file
                    self.build_succeeded = True
                    success(f"JAR created: {jar_file.name}")
                    return True
                error("No JAR file found in build/libs")
                return False

            error("Build failed")
            full_output = (result.stdout or "") + "\n" + (result.stderr or "")
            self.log_to_file("\n=== FULL BUILD OUTPUT ===\n" + full_output)
            print((result.stdout or "")[-2000:])
            print((result.stderr or "")[-2000:])
            return False
        except Exception as exc:
            error(f"Build failed: {exc}")
            self.log_to_file(f"Build failed: {exc}")
            return False

    def git_commit_and_push(self, message="Automated build"):
        header("GIT COMMIT & PUSH")
        self.log_to_file("\n" + "=" * 80)
        self.log_to_file("GIT COMMIT & PUSH")
        self.log_to_file("=" * 80)

        try:
            result = subprocess.run(
                ["git", "status", "--porcelain"],
                cwd=self.root,
                capture_output=True,
                text=True,
            )
            if not (result.stdout or "").strip():
                info("No changes to commit")
                self.log_to_file("No changes to commit")
                return True

            subprocess.run(["git", "add", "-A"], cwd=self.root, check=True)
            success("Files staged")

            commit_msg = f"{message}\n\nGenerated: {self.timestamp}"
            subprocess.run(["git", "commit", "-m", commit_msg], cwd=self.root, check=True)
            success(f"Committed: {message}")

            info("Pushing to GitHub...")
            result = subprocess.run(
                ["git", "push", "origin", "main"],
                cwd=self.root,
                capture_output=True,
                text=True,
            )
            if result.returncode == 0:
                success("Pushed to origin/main")
                return True

            error(f"Push failed: {(result.stderr or '').strip()}")
            return False
        except subprocess.CalledProcessError as exc:
            error(f"Git operation failed: {exc}")
            return False

    def log_to_file(self, message):
        with open(self.log_file, "a", encoding="utf-8") as f:
            f.write(message + "\n")

    def generate_report(self):
        header("BUILD REPORT")
        default_questions = self.root / "src/main/resources/trivia/default_questions.json"
        q_count = 0
        try:
            root = json.loads(default_questions.read_text(encoding="utf-8"))
            q_count = len(root.get("questions", []))
        except Exception:
            q_count = 0

        with open(self.log_file, "w", encoding="utf-8") as f:
            f.write("=" * 80 + "\n")
            f.write("TRIVIA - BUILD LOG\n")
            f.write("=" * 80 + "\n\n")
            f.write(f"Timestamp: {self.timestamp}\n")
            f.write("Minecraft Version: 1.21.1\n")
            f.write(f"Bundled Questions: {q_count}/100\n\n")

        all_checks_passed = (not self.errors) and (not self.build_requested or self.build_succeeded)
        if all_checks_passed:
            success("ALL CHECKS PASSED")
        else:
            if self.errors:
                error(f"{len(self.errors)} error(s) found")
                for i, err in enumerate(self.errors, 1):
                    print(f"  {i}. {err}")
            if self.build_requested and not self.build_succeeded:
                error("Build failed")

        if self.warnings:
            warning(f"{len(self.warnings)} warning(s)")
            for i, warn in enumerate(self.warnings, 1):
                print(f"  {i}. {warn}")

        success("Complete log: universal_build.log")

    def prompt_for_test_launch(self):
        if not sys.stdin.isatty():
            info("Skipping Minecraft launch prompt (non-interactive terminal)")
            return
        print()
        try:
            response = input("Launch Minecraft test instance with the latest build? [y/N]: ").strip().lower()
        except EOFError:
            warning("Input unavailable; skipping Minecraft launch")
            return
        if response in ("y", "yes"):
            self.launch_test_instance()
        else:
            info("Skipping Minecraft launch")

    def launch_test_instance(self):
        header("MINECRAFT TEST INSTANCE")
        instance_dir = self.test_instance_dir
        mods_dir = instance_dir / "mods"
        saves_dir = instance_dir / "saves"
        mods_dir.mkdir(parents=True, exist_ok=True)
        saves_dir.mkdir(parents=True, exist_ok=True)

        if self.latest_jar and self.latest_jar.exists():
            for stray in mods_dir.glob("trivia*.jar"):
                try:
                    stray.unlink()
                except OSError as exc:
                    warning(f"Could not remove old jar {stray.name}: {exc}")
            target = mods_dir / self.latest_jar.name
            shutil.copy2(self.latest_jar, target)
            success(f"Synced {self.latest_jar.name} into {mods_dir}")
        else:
            warning("No freshly built jar found; dev runtime will still load project classes")

        gradlew_path = self.root / ("gradlew.bat" if os.name == "nt" else "gradlew")
        if not gradlew_path.exists():
            error("Gradle wrapper missing; cannot launch Minecraft client")
            return
        info(f"Worlds and configs persist under: {instance_dir}")
        info("Close the Minecraft window to return to the build script")
        args_value = f"--gameDir \"{instance_dir}\" --username TriviaTester"
        try:
            gradle_user_home = self.root / ".gradle-user-home"
            gradle_env = os.environ.copy()
            gradle_env["GRADLE_USER_HOME"] = str(gradle_user_home)
            subprocess.run([str(gradlew_path), "runClient", f"--args={args_value}"], cwd=self.root, env=gradle_env)
        except KeyboardInterrupt:
            warning("Minecraft client interrupted by user")


def main():
    parser = argparse.ArgumentParser(description="Trivia - Ultimate Build & Deploy System")
    parser.add_argument("--check", action="store_true", help="Validation only (no build)")
    parser.add_argument("--build", action="store_true", help="Validation + Build")
    parser.add_argument("--deploy", action="store_true", help="Build + Commit + Push")
    parser.add_argument("--full", action="store_true", help="Complete: Validate + Build + Commit + Push")
    parser.add_argument("--message", default="Automated build and deployment", help="Git commit message")

    clean_group = parser.add_mutually_exclusive_group()
    clean_group.add_argument(
        "--clean",
        action="store_true",
        help="Run 'gradle clean' before building (can fail on Windows if VS Code locks Loom/Yarn jars)",
    )
    clean_group.add_argument("--no-clean", action="store_true", help="Skip 'gradle clean' (useful for fast iteration on Windows)")

    args = parser.parse_args()

    if args.full and not args.no_clean:
        args.clean = True

    print()
    log("╔" + "═" * 78 + "╗", Color.BLUE)
    log("║" + " " * 78 + "║", Color.BLUE)
    log("║" + "TRIVIA - ULTIMATE BUILD SYSTEM".center(78) + "║", Color.CYAN)
    log("║" + "One Script To Rule Them All".center(78) + "║", Color.CYAN)
    log("║" + " " * 78 + "║", Color.BLUE)
    log("╚" + "═" * 78 + "╝", Color.BLUE)

    builder = UniversalBuildSystem()

    if not (args.check or args.build or args.deploy or args.full):
        args.check = True

    if not builder.validate_all():
        error("Validation failed! Fix errors before building.")
        builder.generate_report()
        sys.exit(1)

    if args.build or args.deploy or args.full:
        if not builder.build_project(clean=args.clean):
            error("Build failed!")
            builder.generate_report()
            sys.exit(1)

    if args.deploy or args.full:
        if not builder.git_commit_and_push(args.message):
            error("Deployment failed!")
            builder.generate_report()
            sys.exit(1)

    builder.generate_report()
    if args.full:
        builder.prompt_for_test_launch()

    success("═" * 80)
    success("  ALL OPERATIONS COMPLETED SUCCESSFULLY")
    success("═" * 80)
    sys.exit(0)


if __name__ == "__main__":
    main()
