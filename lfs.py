import os
import subprocess
from flask import Flask, request, jsonify

app = Flask(__name__)

BASE_DIR = r"C:\unleashedagis - Copy\src\atavism\agis"

@app.route('/modify', methods=['POST'])
def modify_file():
    data = request.json
    filename = data.get("filename")
    content = data.get("content")

    if not filename or not content:
        return jsonify({"error": "Missing filename or content"}), 400

    file_path = os.path.join(BASE_DIR, filename)

    # Ensure directory exists
    os.makedirs(os.path.dirname(file_path), exist_ok=True)

    # If file doesn't exist, create it
    if not os.path.exists(file_path):
        with open(file_path, "w", encoding="utf-8") as f:
            f.write("# Created by ChatGPT\n")

    # Append content to the file
    with open(file_path, "a", encoding="utf-8") as f:
        f.write("\n" + content)

    # Run auto_update.py to commit & push changes
    subprocess.run(["python", "auto_update.py"], check=True)

    return jsonify({"message": f"File {filename} updated and pushed to GitHub!"})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5001)
