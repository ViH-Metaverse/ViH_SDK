import UIKit
import AVFoundation

/// Voice-bot call screen. Mirrors Android `ui/activity/VoicebotActivity.kt`.
///
/// Transport is a raw WebSocket (`URLSessionWebSocketTask`) carrying JSON control frames
/// (text) and raw PCM audio (binary): 16 kHz mono s16le uplink, 24 kHz mono s16le downlink.
/// There is no auth and no multiplexing — one socket is one call. Audio capture/playback use
/// `AVAudioEngine`; `AVAudioSession` handles speaker routing (Android's `AudioManager`).
///
/// Lifecycle:
///   1. Caller passes the channel's voice_bot ws_url + bot_key (+ caller first name, agent name).
///   2. Request mic permission, open the socket, send a `start` frame.
///   3. On `ready`, stream the mic uplink; play agent audio back gaplessly as it arrives.
///   4. Hang-up / `call_ended` / socket close → tear everything down.
public final class VoicebotViewController: BaseViewController {

    // Uplink: 16 kHz mono s16le. Downlink: 24 kHz mono s16le.
    private static let inRate: Double = 16000
    private static let outRate: Double = 24000

    private let wsUrl: String
    private let botKey: String
    private let firstName: String
    private let agentName: String

    private var speakerOn = true
    private var muted = false
    private var callEnded = false
    private var micActive = false

    private let orbView = VoicebotOrbView()
    private let statusLabel = UILabel()
    private let agentNameLabel = UILabel()
    private let hangUpButton = UIButton(type: .system)
    private let muteButton = UIButton(type: .system)
    private let speakerButton = UIButton(type: .system)

    // Transport + audio
    private var wsSession: URLSession?
    private var task: URLSessionWebSocketTask?
    private let engine = AVAudioEngine()
    private let playerNode = AVAudioPlayerNode()
    private var captureConverter: AVAudioConverter?
    private var micTapInstalled = false
    private let playbackFormat = AVAudioFormat(
        commonFormat: .pcmFormatFloat32, sampleRate: 24000, channels: 1, interleaved: false
    )!

    public init(wsUrl: String, botKey: String, firstName: String, agentName: String) {
        self.wsUrl = wsUrl
        self.botKey = botKey
        self.firstName = firstName
        self.agentName = agentName
        super.init(nibName: nil, bundle: nil)
    }
    required init?(coder: NSCoder) { fatalError("not supported") }

    // MARK: - UI

    public override func initView() {
        view.backgroundColor = .black
        let title = agentName.isEmpty ? "Voice Assistant" : agentName
        agentNameLabel.text = title
        agentNameLabel.textColor = .white
        agentNameLabel.font = .systemFont(ofSize: 20, weight: .semibold)
        agentNameLabel.textAlignment = .center

        statusLabel.text = "Calling \(title)…"
        statusLabel.textColor = .white
        statusLabel.font = .systemFont(ofSize: 14)
        statusLabel.textAlignment = .center

        orbView.translatesAutoresizingMaskIntoConstraints = false

        hangUpButton.setImage(UIImage(systemName: "phone.down.fill"), for: .normal)
        hangUpButton.tintColor = .systemRed
        hangUpButton.addTarget(self, action: #selector(hangUp), for: .touchUpInside)

        muteButton.setImage(UIImage(systemName: "mic.fill"), for: .normal)
        muteButton.tintColor = .white
        muteButton.addTarget(self, action: #selector(toggleMute), for: .touchUpInside)

        speakerButton.setImage(UIImage(systemName: "speaker.wave.2.fill"), for: .normal)
        speakerButton.tintColor = .white
        speakerButton.addTarget(self, action: #selector(toggleSpeaker), for: .touchUpInside)

        let topStack = UIStackView(arrangedSubviews: [agentNameLabel, statusLabel])
        topStack.axis = .vertical
        topStack.spacing = 8
        topStack.translatesAutoresizingMaskIntoConstraints = false

        let controls = UIStackView(arrangedSubviews: [muteButton, hangUpButton, speakerButton])
        controls.axis = .horizontal
        controls.distribution = .equalSpacing
        controls.translatesAutoresizingMaskIntoConstraints = false

        [topStack, orbView, controls].forEach(view.addSubview)

        NSLayoutConstraint.activate([
            topStack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 32),
            topStack.centerXAnchor.constraint(equalTo: view.centerXAnchor),

            orbView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            orbView.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            orbView.widthAnchor.constraint(equalToConstant: 220),
            orbView.heightAnchor.constraint(equalToConstant: 220),

            controls.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -48),
            controls.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 32),
            controls.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -32),

            hangUpButton.widthAnchor.constraint(equalToConstant: 64),
            hangUpButton.heightAnchor.constraint(equalToConstant: 64)
        ])

        ensurePermissionAndStart()
    }

    private func ensurePermissionAndStart() {
        AVAudioSession.sharedInstance().requestRecordPermission { [weak self] granted in
            DispatchQueue.main.async {
                guard let self = self else { return }
                if granted {
                    self.connect()
                } else {
                    self.showErrorAndFinish("Microphone permission is required to start the voice call")
                }
            }
        }
    }

    // MARK: - Transport

    private func connect() {
        guard !wsUrl.isEmpty, !botKey.isEmpty else {
            showErrorAndFinish("Missing call details")
            return
        }
        guard let url = URL(string: wsUrl) else {
            showErrorAndFinish("Invalid call URL")
            return
        }

        setCallAudioMode()
        do {
            try startAudioEngine()
        } catch {
            CorrelationLogger.warn(message: "audio engine start failed", error: error)
            showErrorAndFinish("Could not start audio")
            return
        }

        let session = URLSession(configuration: .default)
        wsSession = session
        let socket = session.webSocketTask(with: url)
        task = socket
        socket.resume()

        // Send the start frame immediately — URLSession queues it until the socket opens.
        // `outbound` => the agent greets first (correct for tap-to-call).
        let start: [String: Any] = [
            "type": "start",
            "bot": botKey,
            "direction": "outbound",
            "context": ["first_name": firstName]
        ]
        sendJSON(start)
        receiveLoop()
    }

    private func sendJSON(_ object: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: object),
              let text = String(data: data, encoding: .utf8) else { return }
        task?.send(.string(text)) { error in
            if let error = error { CorrelationLogger.warn(message: "ws send failed", error: error) }
        }
    }

    private func receiveLoop() {
        task?.receive { [weak self] result in
            guard let self = self else { return }
            switch result {
            case .failure(let error):
                DispatchQueue.main.async {
                    guard !self.callEnded else { return }
                    CorrelationLogger.warn(message: "ws receive failed", error: error)
                    self.showErrorAndFinish("Connection failed")
                }
            case .success(let message):
                switch message {
                case .string(let text): self.handleControl(text)
                case .data(let data): self.handleAudioDown(data)
                @unknown default: break
                }
                self.receiveLoop()
            }
        }
    }

    private func handleControl(_ text: String) {
        guard let data = text.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let type = obj["type"] as? String else { return }
        switch type {
        case "ready":
            DispatchQueue.main.async {
                self.statusLabel.isHidden = true
                self.orbView.setSpeakingLevel(0.15)
                self.micActive = true // start streaming the mic uplink now
            }
        case "event":
            let name = obj["name"] as? String
            if name == "barge_in" {
                flushPlayback()
            } else if name == "call_ended" {
                DispatchQueue.main.async { self.endCall(remote: true) }
            }
        case "error":
            let message = (obj["message"] as? String) ?? ""
            DispatchQueue.main.async { self.showBusyOrError(message) }
        default:
            break // tts_chunk / *_transcript / assistant_* — audio arrives as binary
        }
    }

    // MARK: - Audio engine

    private func startAudioEngine() throws {
        // Playback: 24 kHz mono float; the mixer resamples to hardware output.
        engine.attach(playerNode)
        engine.connect(playerNode, to: engine.mainMixerNode, format: playbackFormat)

        // Capture: convert the hardware input format down to 16 kHz mono s16le.
        let input = engine.inputNode
        let inputFormat = input.inputFormat(forBus: 0)
        guard let uplinkFormat = AVAudioFormat(
            commonFormat: .pcmFormatInt16, sampleRate: Self.inRate, channels: 1, interleaved: true
        ) else { throw NSError(domain: "voicebot", code: -1) }
        captureConverter = AVAudioConverter(from: inputFormat, to: uplinkFormat)

        input.installTap(onBus: 0, bufferSize: 4096, format: inputFormat) { [weak self] buffer, _ in
            self?.onMicBuffer(buffer, uplinkFormat: uplinkFormat, inputRate: inputFormat.sampleRate)
        }
        micTapInstalled = true

        engine.prepare()
        try engine.start()
        playerNode.play()
    }

    private func onMicBuffer(_ buffer: AVAudioPCMBuffer, uplinkFormat: AVAudioFormat, inputRate: Double) {
        guard micActive, let converter = captureConverter else { return }
        let ratio = Self.inRate / inputRate
        let capacity = AVAudioFrameCount(Double(buffer.frameLength) * ratio + 1024)
        guard let out = AVAudioPCMBuffer(pcmFormat: uplinkFormat, frameCapacity: capacity) else { return }

        var supplied = false
        let inputBlock: AVAudioConverterInputBlock = { _, status in
            if supplied { status.pointee = .noDataNow; return nil }
            supplied = true
            status.pointee = .haveData
            return buffer
        }
        var err: NSError?
        converter.convert(to: out, error: &err, withInputFrom: inputBlock)
        guard err == nil, let channel = out.int16ChannelData, out.frameLength > 0 else { return }

        let byteCount = Int(out.frameLength) * MemoryLayout<Int16>.size
        // Mute streams digital silence (zeroed frames), never stops — the server's VAD must
        // keep hearing the uplink.
        let data = muted ? Data(count: byteCount) : Data(bytes: channel[0], count: byteCount)
        task?.send(.data(data)) { _ in }
    }

    private func handleAudioDown(_ data: Data) {
        guard !callEnded, data.count >= 2 else { return }
        let frameCount = data.count / MemoryLayout<Int16>.size
        guard let buffer = AVAudioPCMBuffer(pcmFormat: playbackFormat, frameCapacity: AVAudioFrameCount(frameCount)),
              let out = buffer.floatChannelData else { return }
        buffer.frameLength = AVAudioFrameCount(frameCount)
        data.withUnsafeBytes { raw in
            let samples = raw.bindMemory(to: Int16.self)
            let dst = out[0]
            for i in 0..<frameCount { dst[i] = Float(samples[i]) / 32768.0 }
        }
        playerNode.scheduleBuffer(buffer, completionHandler: nil)
        if !playerNode.isPlaying { playerNode.play() }
        driveOrbFromAudio()
    }

    /// Barge-in: drop everything buffered/queued so the cut feels immediate.
    private func flushPlayback() {
        playerNode.stop()          // clears scheduled buffers
        playerNode.play()          // ready for the next utterance
        DispatchQueue.main.async {
            NSObject.cancelPreviousPerformRequests(withTarget: self, selector: #selector(self.orbIdle), object: nil)
            self.orbView.setSpeakingLevel(0.15)
        }
    }

    private func driveOrbFromAudio() {
        DispatchQueue.main.async {
            self.orbView.setSpeakingLevel(0.85)
            NSObject.cancelPreviousPerformRequests(withTarget: self, selector: #selector(self.orbIdle), object: nil)
            self.perform(#selector(self.orbIdle), with: nil, afterDelay: 0.25)
        }
    }

    @objc private func orbIdle() { orbView.setSpeakingLevel(0.15) }

    // MARK: - Controls

    @objc private func toggleMute() {
        muted.toggle()
        muteButton.tintColor = muted ? .systemRed : .white
        muteButton.setImage(UIImage(systemName: muted ? "mic.slash.fill" : "mic.fill"), for: .normal)
    }

    @objc private func toggleSpeaker() {
        speakerOn.toggle()
        applySpeakerRouting(speakerOn)
        speakerButton.setImage(
            UIImage(systemName: speakerOn ? "speaker.wave.2.fill" : "speaker.slash.fill"),
            for: .normal
        )
    }

    private func setCallAudioMode() {
        applySpeakerRouting(speakerOn)
    }

    /// Equivalent of `AudioManager.setCommunicationDevice` — routes call audio between the
    /// speaker and earpiece via `AVAudioSession`.
    private func applySpeakerRouting(_ on: Bool) {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playAndRecord, mode: .voiceChat, options: on ? [.defaultToSpeaker] : [])
            try session.setActive(true)
            try session.overrideOutputAudioPort(on ? .speaker : .none)
        } catch {
            CorrelationLogger.warn(message: "applySpeakerRouting failed", error: error)
        }
    }

    // MARK: - Teardown

    @objc private func hangUp() {
        sendJSON(["type": "end"]) // best-effort
        endCall(remote: false)
    }

    private func showBusyOrError(_ message: String) {
        let friendly = message.range(of: "busy", options: .caseInsensitive) != nil
            ? "All lines are busy, please try again."
            : "Call error. Please try again."
        let alert = UIAlertController(title: nil, message: friendly, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default) { [weak self] _ in
            self?.endCall(remote: true)
        })
        present(alert, animated: true)
    }

    private func showErrorAndFinish(_ message: String) {
        let alert = UIAlertController(title: nil, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default) { [weak self] _ in
            self?.endCall(remote: true)
        })
        present(alert, animated: true)
    }

    /// Idempotent full teardown. `remote` just distinguishes who initiated (for logging).
    private func endCall(remote: Bool) {
        if callEnded { return }
        callEnded = true
        micActive = false
        CorrelationLogger.info(message: "endCall remote=\(remote)")
        NSObject.cancelPreviousPerformRequests(withTarget: self, selector: #selector(orbIdle), object: nil)
        teardown()
        dismiss(animated: true)
    }

    private func teardown() {
        if micTapInstalled {
            engine.inputNode.removeTap(onBus: 0)
            micTapInstalled = false
        }
        playerNode.stop()
        if engine.isRunning { engine.stop() }
        task?.cancel(with: .goingAway, reason: nil)
        task = nil
        wsSession?.invalidateAndCancel()
        wsSession = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    deinit { teardown() }
}
