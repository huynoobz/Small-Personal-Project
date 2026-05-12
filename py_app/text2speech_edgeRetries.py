import asyncio
import edge_tts
import random
import re

VOICE = "vi-VN-HoaiMyNeural"  # chỉ dùng 1 voice

# ================= CLEAN =================
def clean_text(text):
    text = text.replace("\u200b", "")
    text = re.sub(r"\s+", " ", text)
    return text.strip()


# ================= SPLIT =================
def split_text_safe(text, max_len):
    chunks = []
    start = 0
    n = len(text)

    while start < n:
        end = start + max_len

        if end >= n:
            chunks.append(text[start:].strip())
            break

        cut = text.rfind("\n", start, end)

        if cut == -1 or cut < start + max_len * 0.5:
            cut = max(
                text.rfind(". ", start, end),
                text.rfind("! ", start, end),
                text.rfind("? ", start, end)
            )

        if cut == -1 or cut < start + max_len * 0.5:
            cut = text.rfind(", ", start, end)

        if cut == -1 or cut <= start:
            cut = end
            while cut > start and text[cut] not in " \n":
                cut -= 1

        chunk = text[start:cut].strip()
        if chunk:
            chunks.append(chunk)

        start = cut

    return chunks


# ================= SINGLE CALL =================
async def send_once(text):
    communicate = edge_tts.Communicate(text, VOICE)

    audio = b""
    audio_found = False

    async for msg in communicate.stream():
        if msg["type"] == "audio":
            audio += msg["data"]
            audio_found = True

    if not audio_found:
        raise RuntimeError("No audio")

    return audio


# ================= RETRY CHUNK =================
async def process_chunk(chunk, idx):

    attempt = 0
    sizes = [2000, 1200, 800, 400, 200]

    while True:
        attempt += 1

        size = sizes[min(attempt // 5, len(sizes) - 1)]
        sub_chunks = split_text_safe(chunk, size)

        print(f"[CHUNK {idx}] attempt {attempt} | size={size} | parts={len(sub_chunks)}")

        try:
            result = b""

            for sub in sub_chunks:
                audio = await send_once(sub)
                result += audio

                # jitter delay
                await asyncio.sleep(0.2 + random.uniform(0, 0.4))

            print(f"[CHUNK {idx}] SUCCESS after {attempt} attempts")
            return result

        except Exception as e:
            print(f"[CHUNK {idx}] FAIL attempt {attempt}: {e}")

            # backoff tăng dần
            sleep_time = min(5, 0.5 * attempt) + random.uniform(0, 1)
            await asyncio.sleep(sleep_time)


# ================= MAIN =================
async def txt_to_audio(input_file, output_file):

    with open(input_file, "r", encoding="utf-8") as f:
        text = clean_text(f.read())

    chunks = split_text_safe(text, 1000)

    with open(output_file, "wb") as out:

        for i, chunk in enumerate(chunks):
            print(f"\n=== PROCESS CHUNK {i+1}/{len(chunks)} ===")

            audio = await process_chunk(chunk, i)
            out.write(audio)


# ================= RUN =================
asyncio.run(txt_to_audio("", ""))