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
            choices = {c['label']:c['text'] for c in data["question"]["choices"]}
            answer = choices[data['answerKey']]
            questions[key] = (sent, answer)



    for k, v in questions.items():
        print(f"{k}\t{v[0]}\t{v[1]}")