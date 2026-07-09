let recorderWindowId = null;

function createRecorderWindow() {
  chrome.windows.create(
    {
      url: chrome.runtime.getURL('popup.html'),
      type: 'popup',
      width: 360,
      height: 420,
    }, (windowInfo) => {
      if (windowInfo && windowInfo.id !== undefined) {
        recorderWindowId = windowInfo.id;
      }
    }
  );
}

chrome.action.onClicked.addListener(() => {
  if (recorderWindowId !== null) {
    chrome.windows.get(recorderWindowId, { populate: false }, (windowInfo) => {
      if (chrome.runtime.lastError || !windowInfo) {
        recorderWindowId = null;
        createRecorderWindow();
        return;
      }
      chrome.windows.update(recorderWindowId, { focused: true });
    });
  } else {
    createRecorderWindow();
  }
});

chrome.windows.onRemoved.addListener((windowId) => {
  if (windowId === recorderWindowId) {
    recorderWindowId = null;
  }
});
