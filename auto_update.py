import os
import subprocess

# Local repository directory
LOCAL_REPO_DIR = r"C:\unleashedagis - Copy\src\atavism\agis"

# Change to repo directory
os.chdir(LOCAL_REPO_DIR)

# Check for changes
status_output = subprocess.run(["git", "status", "--porcelain"], capture_output=True, text=True)

# If there are changes, commit and push
if status_output.stdout.strip():
    subprocess.run(["git", "add", "."], check=True)
    subprocess.run(["git", "commit", "-m", "Automated push from ChatGPT"], check=True)
    subprocess.run(["git", "push", "origin", "master"], check=True)
    print("✅ Changes pushed to GitHub!")
else:
    print("⚠ No changes detected, skipping push.")
