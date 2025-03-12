import os
import base64
import requests
import subprocess
import datetime

# GitHub credentials
GITHUB_TOKEN = "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"
REPO_OWNER = "WorldOfEpoch"
REPO_NAME = "agis"
BRANCH = "master"

# Local repo directory
LOCAL_REPO_DIR = r"C:\unleashedagis - Copy\src\atavism\agis"

# Change working directory to repo
os.chdir(LOCAL_REPO_DIR)

# Modify only certain file types (adjust as needed)
ALLOWED_EXTENSIONS = {".txt", ".cs", ".json", ".xml", ".py"}  # Include .py for new scripts

# Track whether files are modified or new
modified_files = []
new_files = []

# Loop through all files in the directory
for root, _, files in os.walk(LOCAL_REPO_DIR):
    for file in files:
        if file.endswith(tuple(ALLOWED_EXTENSIONS)):  # Modify only specific files
            file_path = os.path.join(root, file)

            # Read file content
            with open(file_path, "r", encoding="utf-8") as f:
                content = f.read()

            # Modify content (force change)
            new_content = content + f"\n# Updated by ChatGPT on {datetime.datetime.now()}!"

            if new_content != content:  # Only modify if changes are made
                # Save updated file
                with open(file_path, "w", encoding="utf-8") as f:
                    f.write(new_content)

                modified_files.append(file_path)

# Detect untracked files
status_output = subprocess.run(["git", "status", "--porcelain"], capture_output=True, text=True)
for line in status_output.stdout.splitlines():
    if line.startswith("??"):  # Untracked files start with ??
        new_files.append(line.split("?? ")[1].strip())

# Add all modified and new files to Git
if modified_files or new_files:
    subprocess.run(["git", "add", "."], check=True)  # Stage all changes, including new files
    commit_message = f"Automated update via ChatGPT on {datetime.datetime.now()}"
    subprocess.run(["git", "commit", "-m", commit_message], check=True)
    subprocess.run(["git", "push", "origin", BRANCH], check=True)
    print("✅ Changes detected, committed, and pushed successfully!")
else:
    print("⚠ No changes detected, skipping commit.")


# Updated by ChatGPT on 2025-03-11 22:54:41.617222!
# Updated by ChatGPT on 2025-03-11 22:58:57.661708!