import sys
import json

if __name__ == "__main__":
    input_path = sys.argv[1]
    questions = dict()
    with open(input_path) as f:
        for l in f:
            data = json.loads(l)
            key = data["id"]
            sent = data["question"]['stem']
            questions[key] = sent

    for k, v in questions.items():
        print(f"{k}\t{v}")    