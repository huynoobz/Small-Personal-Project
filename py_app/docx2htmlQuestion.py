from docx import Document
import re
import json

def is_bold(paragraph):
    # kiểm tra nếu bất kỳ run nào trong đoạn có bold
    for run in paragraph.runs:
        if run.bold:
            return True
    return False

def parse_docx(file_path):
    doc = Document(file_path)
    questions = []

    current_q = None
    options = []
    answer = 0

    answer_map = {"A":0, "B":1, "C":2, "D":3, "a":0, "b":1, "c":2, "d":3}

    for para in doc.paragraphs:
      for text, is_bold_line in get_lines_with_format(para):

        q_match = re.match(r'^\s*câu\s+\d+[\.\:\-]?\s*(.+)$', text, re.IGNORECASE)
        if q_match:
            if current_q and len(options) == 4 and answer is not None:
                questions.append({
                    "q": current_q,
                    "options": options,
                    "answer": answer
                })

            current_q = q_match.group(1)
            options = []
            answer = None
            continue

        opt_match = re.match(r'^\s*([a-d])[\.\)]\s*(.+)$', text, re.IGNORECASE)
        if opt_match:
            letter = opt_match.group(1).upper()
            content = opt_match.group(2)

            options.append(content)

            if is_bold_line:
                answer = answer_map[letter]
    # thêm câu cuối
    if current_q and len(options) == 4:
        questions.append({
            "q": current_q,
            "options": options,
            "answer": answer
        })

    return questions

def get_lines_with_format(paragraph):
    lines = []
    current_line = ""
    current_bold = False

    for run in paragraph.runs:
        parts = run.text.split('\n')

        for i, part in enumerate(parts):
            if part:
                current_line += part
                if run.bold:
                    current_bold = True

            if i < len(parts) - 1:
                lines.append((current_line.strip(), current_bold))
                current_line = ""
                current_bold = False

    if current_line:
        lines.append((current_line.strip(), current_bold))

    return lines

def export_js(questions):
    return "const questions = " + json.dumps(questions, ensure_ascii=False, indent=2) + ";"


def docx2quest(file_path):
    questions = parse_docx(file_path)

    with open("questions.js", "w", encoding="utf-8") as f:
        f.write(export_js(questions))

    print(f"Đã xử lý {len(questions)} câu")