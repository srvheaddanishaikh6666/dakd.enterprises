/**
 * DAKD JARVIS - Voice Controller
 * Integrates Android native SpeechRecognizer & TextToSpeech with visual feedback
 */
const JarvisVoice = (function() {
  let isListening = false;
  let isMuted = false;
  let webSpeechRecognizer = null;

  function init() {
    // Check web speech fallback if not on Android
    if (!JarvisStorage.isNative() && ('webkitSpeechRecognition' in window || 'SpeechRecognition' in window)) {
      const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
      webSpeechRecognizer = new SpeechRecognition();
      webSpeechRecognizer.continuous = false;
      webSpeechRecognizer.interimResults = false;
      webSpeechRecognizer.lang = 'hi-IN';

      webSpeechRecognizer.onstart = () => {
        setListening(true);
      };
      webSpeechRecognizer.onend = () => {
        setListening(false);
      };
      webSpeechRecognizer.onerror = (e) => {
        setListening(false);
        JarvisUI.showCoreStatus('MIC ERROR');
      };
      webSpeechRecognizer.onresult = (event) => {
        const text = event.results[0][0].transcript;
        setListening(false);
        JarvisCommands.handleUserQuery(text);
      };
    }
  }

  function toggleListening() {
    if (isListening) {
      stopListening();
    } else {
      startListening();
    }
  }

  function startListening() {
    if (JarvisStorage.isNative() && typeof window.AndroidBridge.startVoiceRecognition === 'function') {
      window.AndroidBridge.startVoiceRecognition('hi-IN');
      setListening(true);
    } else if (webSpeechRecognizer) {
      try {
        webSpeechRecognizer.start();
        setListening(true);
      } catch (e) {
        console.warn('Web speech recognizer already active');
      }
    } else {
      // Prompt user or mock voice
      const sample = prompt("Simulate Voice Input (or type command in bottom box):", "WhatsApp kholo");
      if (sample) {
        JarvisCommands.handleUserQuery(sample);
      }
    }
  }

  function stopListening() {
    if (JarvisStorage.isNative() && typeof window.AndroidBridge.stopVoiceRecognition === 'function') {
      window.AndroidBridge.stopVoiceRecognition();
    } else if (webSpeechRecognizer) {
      try { webSpeechRecognizer.stop(); } catch (e) {}
    }
    setListening(false);
  }

  function setListening(active) {
    isListening = active;
    const micBtn = document.getElementById('mic-button');
    const micHint = document.getElementById('mic-hint');
    const waveRing = document.getElementById('wave-ring');

    if (active) {
      micBtn.classList.add('listening');
      micHint.textContent = 'Listening... Speak now';
      JarvisUI.showCoreStatus('LISTENING...');
      if (waveRing) {
        waveRing.style.borderColor = 'rgba(255, 51, 102, 0.7)';
        waveRing.style.transform = 'scale(1.2)';
      }
    } else {
      micBtn.classList.remove('listening');
      micHint.textContent = 'Tap to Speak (Hindi / English)';
      JarvisUI.showCoreStatus('ONLINE');
      if (waveRing) {
        waveRing.style.borderColor = 'transparent';
        waveRing.style.transform = 'scale(1.0)';
      }
    }
  }

  function updateRms(rms) {
    const waveRing = document.getElementById('wave-ring');
    if (!waveRing) return;
    // Scale RMS to wave diameter
    const scale = Math.min(1.4, Math.max(1.0, 1.0 + (rms / 15.0)));
    waveRing.style.transform = `scale(${scale})`;
    waveRing.style.borderColor = `rgba(0, 240, 255, ${Math.min(0.9, 0.3 + (rms / 12.0))})`;
  }

  function speak(text) {
    if (isMuted) return;
    if (JarvisStorage.isNative() && typeof window.AndroidBridge.speakText === 'function') {
      window.AndroidBridge.speakText(text);
    } else if ('speechSynthesis' in window) {
      window.speechSynthesis.cancel();
      const utterance = new SpeechSynthesisUtterance(text);
      utterance.lang = 'hi-IN';
      window.speechSynthesis.speak(utterance);
    }
  }

  function stopSpeaking() {
    if (JarvisStorage.isNative() && typeof window.AndroidBridge.stopSpeaking === 'function') {
      window.AndroidBridge.stopSpeaking();
    } else if ('speechSynthesis' in window) {
      window.speechSynthesis.cancel();
    }
  }

  function toggleMute() {
    isMuted = !isMuted;
    const btn = document.getElementById('btn-sound-toggle');
    if (isMuted) {
      stopSpeaking();
      btn.classList.add('active');
    } else {
      btn.classList.remove('active');
    }
    return isMuted;
  }

  return {
    init,
    toggleListening,
    startListening,
    stopListening,
    setListening,
    updateRms,
    speak,
    stopSpeaking,
    toggleMute
  };
})();
