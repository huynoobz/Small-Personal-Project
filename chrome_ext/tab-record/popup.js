const startBtn = document.getElementById('startBtn');
const stopBtn = document.getElementById('stopBtn');
const downloadBtn = document.getElementById('downloadBtn');
const refreshTabsBtn = document.getElementById('refreshTabsBtn');
const tabSelect = document.getElementById('tabSelect');
const previewVideo = document.getElementById('previewVideo');
const previewAudio = document.getElementById('previewAudio');
const statusEl = document.getElementById('status');
const audioCheckbox = document.getElementById('audioEnabled');
const videoCheckbox = document.getElementById('videoEnabled');

let recorder;
let recordedChunks = [];
let recordedBlob = null;
let capturedStream;
let downloadUrl;

function setStatus(message) {
  statusEl.textContent = message;
}

function getSupportedMimeType(audio, video) {
  const candidates = [];
  if (video) {
    candidates.push('video/webm;codecs=vp8,opus', 'video/webm;codecs=vp8', 'video/webm');
  } else {
    candidates.push('audio/webm;codecs=opus', 'audio/webm');
  }
  return candidates.find(type => MediaRecorder.isTypeSupported(type)) || '';
}

function readId(view, pos) {
  const first = view.getUint8(pos);
  let mask = 0x80;
  let length = 1;
  while (length < 4 && !(first & mask)) {
    mask >>= 1;
    length += 1;
  }
  if (!(first & mask)) return null;
  let value = first;
  for (let i = 1; i < length; i += 1) {
    value = (value << 8) | view.getUint8(pos + i);
  }
  return { id: value, length };
}

function readVint(view, pos) {
  const first = view.getUint8(pos);
  let mask = 0x80;
  let length = 1;
  while (length < 8 && !(first & mask)) {
    mask >>= 1;
    length += 1;
  }
  if (!(first & mask)) return null;
  let value = first & (mask - 1);
  for (let i = 1; i < length; i += 1) {
    value = (value << 8) | view.getUint8(pos + i);
  }
  const maxValue = (1 << (7 * length)) - 1;
  return { length, value: value === maxValue ? null : value };
}

function readElement(view, pos) {
  const idInfo = readId(view, pos);
  if (!idInfo) return null;
  const sizeInfo = readVint(view, pos + idInfo.length);
  if (!sizeInfo) return null;
  const dataOffset = pos + idInfo.length + sizeInfo.length;
  return {
    id: idInfo.id,
    idLength: idInfo.length,
    size: sizeInfo.value,
    sizeLength: sizeInfo.length,
    dataOffset,
    totalSize: sizeInfo.value === null ? null : dataOffset + sizeInfo.value,
    start: pos,
  };
}

function readUint(view, pos, length) {
  let value = 0;
  for (let i = 0; i < length; i += 1) {
    value = (value << 8) | view.getUint8(pos + i);
  }
  return value;
}

function uintToBytes(value) {
  if (value === 0) return new Uint8Array([0]);
  const bytes = [];
  while (value > 0) {
    bytes.unshift(value & 0xff);
    value >>= 8;
  }
  return new Uint8Array(bytes);
}

function makeElement(id, payload) {
  const idBytes = uintToBytes(id);
  const sizeBytes = uintToBytes(payload.length);
  const sizeBuffer = new Uint8Array(sizeBytes.length);
  for (let i = 0; i < sizeBytes.length; i += 1) {
    sizeBuffer[i] = sizeBytes[i];
  }
  sizeBuffer[0] |= 1 << (8 - sizeBytes.length);
  return concatUint8([idBytes, sizeBuffer, payload]);
}

function concatUint8(chunks) {
  let length = 0;
  chunks.forEach(chunk => { length += chunk.length; });
  const result = new Uint8Array(length);
  let offset = 0;
  chunks.forEach(chunk => {
    result.set(chunk, offset);
    offset += chunk.length;
  });
  return result;
}

function parseWebMClusters(view, segmentStart, segmentSize) {
  const clusters = [];
  let pos = segmentStart;
  const segmentEnd = segmentSize === null ? view.byteLength : segmentStart + segmentSize;
  while (pos < segmentEnd) {
    const element = readElement(view, pos);
    if (!element || element.totalSize === null) break;
    if (element.id === 0x1f43b675) {
      const timecode = parseClusterTimecode(view, element.dataOffset, element.totalSize);
      clusters.push({ position: pos - segmentStart, timecode });
    } else if (element.id === 0x1c53bb6b) {
      return { hasCues: true };
    }
    pos = element.totalSize;
  }
  return { clusters, hasCues: false };
}

function parseClusterTimecode(view, start, end) {
  let pos = start;
  while (pos < end) {
    const element = readElement(view, pos);
    if (!element || element.totalSize === null) break;
    if (element.id === 0xe7) {
      return readUint(view, element.dataOffset, element.size);
    }
    pos = element.totalSize;
  }
  return 0;
}

function makeVint(value) {
  const bytes = uintToBytes(value);
  const length = bytes.length;
  const result = new Uint8Array(length);
  for (let i = 0; i < length; i += 1) {
    result[i] = bytes[i];
  }
  result[0] |= 1 << (8 - length);
  return result;
}

function makeUintElement(id, value) {
  return makeElement(id, uintToBytes(value));
}

function makeCuePoint(cluster, trackNumber) {
  const cueTime = makeUintElement(0xb3, cluster.timecode);
  const cueTrackPositions = makeElement(0xb7, concatUint8([
    makeUintElement(0xf7, trackNumber),
    makeUintElement(0xf1, cluster.position),
  ]));
  return makeElement(0xbb, concatUint8([cueTime, cueTrackPositions]));
}

function buildCues(clusters, trackNumber) {
  const cuePoints = concatUint8(clusters.map(cluster => makeCuePoint(cluster, trackNumber)));
  return makeElement(0x1c53bb6b, cuePoints);
}

function parseTrackNumber(view, segmentStart, segmentSize) {
  let pos = segmentStart;
  const segmentEnd = segmentSize === null ? view.byteLength : segmentStart + segmentSize;
  while (pos < segmentEnd) {
    const element = readElement(view, pos);
    if (!element || element.totalSize === null) break;
    if (element.id === 0x1654ae6b) {
      return parseTrackEntry(view, element.dataOffset, element.totalSize);
    }
    pos = element.totalSize;
  }
  return 1;
}

function parseTrackEntry(view, start, end) {
  let pos = start;
  const segmentEnd = end;
  while (pos < segmentEnd) {
    const element = readElement(view, pos);
    if (!element || element.totalSize === null) break;
    if (element.id === 0xae) {
      const trackNumber = parseTrackNumberFromEntry(view, element.dataOffset, element.totalSize);
      if (trackNumber) return trackNumber;
    }
    pos = element.totalSize;
  }
  return 1;
}

function parseTrackNumberFromEntry(view, start, end) {
  let pos = start;
  const segmentEnd = end;
  while (pos < segmentEnd) {
    const element = readElement(view, pos);
    if (!element || element.totalSize === null) break;
    if (element.id === 0xd7) {
      return readUint(view, element.dataOffset, element.size);
    }
    pos = element.totalSize;
  }
  return null;
}

async function fixWebM(blob) {
  if (!blob.type.includes('webm')) return blob;
  const buffer = await blob.arrayBuffer();
  const view = new DataView(buffer);
  let pos = 0;
  let segmentStart = null;
  let segmentSize = null;
  let segmentSizeLength = null;
  while (pos < view.byteLength) {
    const element = readElement(view, pos);
    if (!element || element.totalSize === null) break;
    if (element.id === 0x18538067) {
      segmentStart = element.dataOffset;
      segmentSize = element.size;
      segmentSizeLength = element.sizeLength;
      break;
    }
    pos = element.totalSize;
  }
  if (segmentStart === null) return blob;
  const { clusters, hasCues } = parseWebMClusters(view, segmentStart, segmentSize);
  if (hasCues || !clusters.length) return blob;
  const trackNumber = parseTrackNumber(view, segmentStart, segmentSize);
  const cuesPayload = buildCues(clusters, trackNumber);
  if (segmentSize !== null && segmentSizeLength !== null) {
    const newSegmentSize = segmentSize + cuesPayload.length;
    const sizePos = segmentStart - segmentSizeLength;
    const newSizeBytes = makeVint(newSegmentSize);
    if (newSizeBytes.length !== segmentSizeLength) {
      return blob;
    }
    const fixedBuffer = new Uint8Array(buffer.byteLength + cuesPayload.length);
    fixedBuffer.set(new Uint8Array(buffer), 0);
    fixedBuffer.set(cuesPayload, buffer.byteLength);
    fixedBuffer.set(newSizeBytes, sizePos);
    return new Blob([fixedBuffer], { type: blob.type });
  }
  return new Blob([buffer, cuesPayload], { type: blob.type });
}

function setButtons(state) {
  if (state === 'idle') {
    startBtn.disabled = false;
    stopBtn.disabled = true;
    downloadBtn.disabled = recordedChunks.length === 0;
  } else if (state === 'recording') {
    startBtn.disabled = true;
    stopBtn.disabled = false;
    downloadBtn.disabled = true;
  } else if (state === 'ready') {
    startBtn.disabled = false;
    stopBtn.disabled = true;
    downloadBtn.disabled = recordedChunks.length === 0;
  }
}

function formatTabLabel(tab) {
  const title = tab.title || tab.url || 'Untitled tab';
  const trimmedTitle = title.length > 60 ? `${title.slice(0, 57)}...` : title;
  return `${trimmedTitle} — ${tab.url ? new URL(tab.url).hostname : 'no URL'}`;
}

function populateTabSelect(tabs) {
  tabSelect.innerHTML = '';
  if (!tabs || tabs.length === 0) {
    const option = document.createElement('option');
    option.textContent = 'No tabs available';
    option.value = '';
    tabSelect.appendChild(option);
    return;
  }

  tabs.forEach(tab => {
    const option = document.createElement('option');
    option.value = tab.id;
    option.textContent = formatTabLabel(tab);
    tabSelect.appendChild(option);
  });
}

function loadTabs() {
  setStatus('Loading available tabs...');
  chrome.tabs.query({}, tabs => {
    if (chrome.runtime.lastError) {
      setStatus('Unable to load tabs.');
      console.error(chrome.runtime.lastError);
      return;
    }

    const visibleTabs = tabs.filter(tab => tab.url && !tab.url.startsWith('chrome://') && !tab.url.startsWith('about:'));
    populateTabSelect(visibleTabs);
    if (visibleTabs.length === 0) {
      setStatus('No recordable tabs found.');
    } else {
      setStatus('Ready to record. Select a tab and click Start.');
    }
  });
}

function getSelectedTabId() {
  const value = Number(tabSelect.value);
  return Number.isInteger(value) && value > 0 ? value : null;
}

function activateTabForCapture(tabId) {
  return new Promise((resolve, reject) => {
    chrome.tabs.get(tabId, tab => {
      if (chrome.runtime.lastError || !tab) {
        return reject(new Error('Unable to access the selected tab.'));
      }

      chrome.tabs.update(tabId, { active: true }, updatedTab => {
        if (chrome.runtime.lastError || !updatedTab) {
          return reject(new Error('Unable to activate the selected tab.'));
        }

        chrome.windows.update(updatedTab.windowId, { focused: true }, () => {
          if (chrome.runtime.lastError) {
            return reject(new Error('Unable to focus the selected tab window.'));
          }
          resolve(updatedTab);
        });
      });
    });
  });
}

async function startRecording() {
  const audio = audioCheckbox.checked;
  const video = videoCheckbox.checked;
  const tabId = getSelectedTabId();

  if (!audio && !video) {
    setStatus('Select audio, video, or both before recording.');
    return;
  }

  if (!tabId) {
    setStatus('Choose a tab to record from the selector.');
    return;
  }

  setStatus('Activating selected tab...');
  try {
    await activateTabForCapture(tabId);
  } catch (error) {
    setStatus(error.message || 'Failed to activate the selected tab.');
    console.error(error);
    return;
  }

  setStatus('Requesting tab capture...');
  chrome.tabCapture.capture({ audio, video }, stream => {
    if (!stream) {
      setStatus('Unable to capture the selected tab. Make sure it is active and allowed.');
      console.error('tabCapture error:', chrome.runtime.lastError);
      return;
    }

    capturedStream = stream;
    recordedChunks = [];
    recordedBlob = null;

    const mimeType = getSupportedMimeType(audio, video);
    try {
      recorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream);
    } catch (error) {
      setStatus('MediaRecorder is not supported for the chosen capture settings.');
      console.error(error);
      stream.getTracks().forEach(track => track.stop());
      return;
    }

    recorder.ondataavailable = event => {
      if (event.data && event.data.size > 0) {
        recordedChunks.push(event.data);
      }
    };

    recorder.onerror = event => {
      setStatus('Recording error: ' + (event.error?.message || 'unknown error'));
      console.error('MediaRecorder error:', event.error);
    };

    recorder.onstop = () => {
      recordedBlob = new Blob(recordedChunks, { type: recordedChunks[0]?.type || (video ? 'video/webm' : 'audio/webm') });
      setStatus('Recording stopped. Click Download to save the file.');
      setButtons('ready');
      if (capturedStream) {
        capturedStream.getTracks().forEach(track => track.stop());
        capturedStream = null;
      }
      stopPreview();
    };

    startPreview(stream, audio, video);
    recorder.start();
    setStatus('Recording and monitoring... keep this window open.');
    setButtons('recording');
  });
}

function stopRecording() {
  if (!recorder || recorder.state !== 'recording') {
    setStatus('Nothing is recording right now.');
    return;
  }

  recorder.requestData();
  recorder.stop();
}

function startPreview(stream, audio, video) {
  if (video) {
    previewVideo.hidden = false;
    previewAudio.hidden = true;
    previewVideo.srcObject = stream;
    previewVideo.muted = false;
    previewVideo.play().catch(() => {});
  } else if (audio) {
    previewAudio.hidden = false;
    previewVideo.hidden = true;
    previewAudio.srcObject = stream;
    previewAudio.play().catch(() => {});
  }
}

function stopPreview() {
  if (previewVideo.srcObject) {
    previewVideo.pause();
    previewVideo.srcObject = null;
    previewVideo.hidden = true;
  }
  if (previewAudio.srcObject) {
    previewAudio.pause();
    previewAudio.srcObject = null;
    previewAudio.hidden = true;
  }
}

async function downloadRecording() {
  if (recordedChunks.length === 0) {
    setStatus('No recorded data available.');
    return;
  }

  setStatus('Copying and fixing WebM time index before download...');
  const originalBlob = recordedBlob || new Blob(recordedChunks, { type: recordedChunks[0]?.type || 'video/webm' });
  const copyBuffer = await originalBlob.arrayBuffer();
  const copiedBlob = new Blob([copyBuffer], { type: originalBlob.type });
  let fixedBlob;
  try {
    fixedBlob = await fixWebM(copiedBlob);
  } catch (error) {
    console.warn('WebM fix failed, downloading copied original:', error);
    fixedBlob = copiedBlob;
  }

  const url = URL.createObjectURL(fixedBlob);
  const filename = audioCheckbox.checked && !videoCheckbox.checked ? 'tab-audio.webm' : 'tab-recording.webm';

  if (downloadUrl) {
    URL.revokeObjectURL(downloadUrl);
  }
  downloadUrl = url;

  const anchor = document.createElement('a');
  anchor.style.display = 'none';
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);

  setStatus('Download started.');
}

startBtn.addEventListener('click', startRecording);
stopBtn.addEventListener('click', stopRecording);
downloadBtn.addEventListener('click', downloadRecording);
refreshTabsBtn.addEventListener('click', loadTabs);

setButtons('idle');
loadTabs();
