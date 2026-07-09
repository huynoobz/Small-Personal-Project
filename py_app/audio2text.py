#!/usr/bin/env python3

import sys
from faster_whisper import WhisperModel


def transcribe(input_path, output_path):
    print("Loading model...")

    model = WhisperModel(
        "large-v3",
        device="cpu",
        compute_type="int8"
    )

    print(f"Processing: {input_path}")

    segments, info = model.transcribe(
        input_path,
        language="vi",
        vad_filter=True,
        beam_size=1,
        temperature=0,
        condition_on_previous_text=False
    )

    print(
        f"Detected language: "
        f"{info.language} "
        f"({info.language_probability:.2%})"
    )

    kept = 0
    skipped = 0

    with open(output_path, "w", encoding="utf-8") as f:

        for segment in segments:

            # Bỏ các đoạn độ tin cậy thấp
            if segment.avg_logprob < -1.0:
                skipped += 1
                continue

            text = segment.text.strip()

            if not text:
                skipped += 1
                continue

            kept += 1

            print(
                f"[{segment.start:8.1f}s"
                f" -> {segment.end:8.1f}s] "
                f"{text}"
            )

            f.write(text + "\n")

    print()
    print(f"Saved: {output_path}")
    print(f"Segments kept: {kept}")
    print(f"Segments skipped: {skipped}")


if __name__ == "__main__":

    if len(sys.argv) != 3:
        print(
            f"Usage: {sys.argv[0]} "
            "<input_audio> <output_txt>"
        )
        sys.exit(1)

    transcribe(
        sys.argv[1],
        sys.argv[2]
    )
