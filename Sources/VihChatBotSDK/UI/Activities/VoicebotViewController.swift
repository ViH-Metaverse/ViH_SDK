import UIKit
import AVFoundation

/// Mirrors `ui/activity/VoicebotActivity.kt`. Flow:
///   1. POST /main/loan-approval/ with `session_id` to mint an Ultravox call.
///   2. Open the WebRTC voice channel via the Ultravox iOS SDK (placeholder
///      hook here — wire `UltravoxSession` once the SwiftPM package is added).
///   3. POST /main/call-details/ on hang-up or server disconnect (idempotent).
///
/// `AVAudioSession` replaces Android's `AudioManager` for speaker routing.
public final class VoicebotViewController: BaseViewController {

    /// Public mirror of `ai.ultravox.UltravoxSessionStatus`. Lets us code the
    /// state machine before the iOS SDK is wired.
    public enum UltravoxStatus { case connecting, disconnecting, disconnected, idle, listening, thinking, speaking }

    private static let agentName = "ViH Shruti"

    private let sessionId: String
    private var callId: String?
    private var callDetailsFired = false
    private var speakerOn = true
    private var speakerInitialised = false

    private let orbView = VoicebotOrbView()
    private let statusLabel = UILabel()
    private let agentNameLabel = UILabel()
    private let hangUpButton = UIButton(type: .system)
    private let muteButton = UIButton(type: .system)
    private let speakerButton = UIButton(type: .system)

    /// Placeholder for the live Ultravox session (the Android equivalent is
    /// `ai.ultravox.UltravoxSession`). Plug in the iOS SDK once available.
    private var session: AnyObject?

    public init(sessionId: String) {
        self.sessionId = sessionId
        super.init(nibName: nil, bundle: nil)
    }
    required init?(coder: NSCoder) { fatalError("not supported") }

    public override func initView() {
        view.backgroundColor = .black
        agentNameLabel.text = Self.agentName
        agentNameLabel.textColor = .white
        agentNameLabel.font = .systemFont(ofSize: 20, weight: .semibold)
        agentNameLabel.textAlignment = .center

        statusLabel.text = "Calling \(Self.agentName)…"
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
                    self.startConnectFlow()
                } else {
                    self.showErrorAndFinish("Microphone permission is required to start the voice call")
                }
            }
        }
    }

    private func startConnectFlow() {
        guard !sessionId.isEmpty else {
            showErrorAndFinish("Missing session id")
            return
        }
        Task { [weak self] in
            guard let self = self else { return }
            do {
                let response = try await APIClient.shared.apiService.postLoanApproval(
                    url: BaseAPIConstants.loanApprovalURL,
                    body: LoanApprovalRequest(session_id: self.sessionId)
                )
                let url = response.voiceWsUrl?.isEmpty == false ? response.voiceWsUrl : response.data?.url
                let id = response.callId?.isEmpty == false ? response.callId : response.data?.callId
                guard let url = url, !url.isEmpty, let id = id, !id.isEmpty else {
                    await MainActor.run { self.showErrorAndFinish("Invalid loan-approval response") }
                    return
                }
                await MainActor.run {
                    self.callId = id
                    self.connectToUltravox(joinUrl: url)
                }
            } catch {
                await MainActor.run { self.showErrorAndFinish("Could not reach loan-approval service") }
            }
        }
    }

    private func connectToUltravox(joinUrl: String) {
        // TODO: Replace with `UltravoxSession(...).joinCall(joinUrl)` once the
        // Ultravox iOS SwiftPM package is added in Package.swift.
        CorrelationLogger.info(message: "ultravox joinCall placeholder url=\(joinUrl)")
        apply(status: .connecting)
    }

    /// Mirrors Android `applyStatus(...)`. The orb pulse amplitude is tied to
    /// the call lifecycle since the SDK doesn't expose raw audio levels.
    public func apply(status: UltravoxStatus) {
        if !speakerInitialised, status == .listening || status == .speaking || status == .idle {
            speakerInitialised = true
            applySpeakerRouting(speakerOn)
        }
        switch status {
        case .disconnected:
            orbView.setSpeakingLevel(0)
            fireCallDetails()
            dismiss(animated: true)
        case .disconnecting:
            orbView.setSpeakingLevel(0)
        case .connecting:
            statusLabel.isHidden = false
            statusLabel.text = "Connecting…"
        case .idle:
            statusLabel.isHidden = true
            orbView.setSpeakingLevel(0.1)
        case .listening:
            statusLabel.isHidden = true
            orbView.setSpeakingLevel(0.2)
        case .thinking:
            statusLabel.isHidden = true
            orbView.setSpeakingLevel(0.45)
        case .speaking:
            statusLabel.isHidden = true
            orbView.setSpeakingLevel(0.8)
        }
    }

    @objc private func toggleSpeaker() {
        speakerOn.toggle()
        applySpeakerRouting(speakerOn)
        speakerButton.setImage(
            UIImage(systemName: speakerOn ? "speaker.wave.2.fill" : "speaker.slash.fill"),
            for: .normal
        )
    }

    /// Equivalent of `AudioManager.setCommunicationDevice` — routes call audio
    /// between speaker and earpiece via `AVAudioSession`.
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

    @objc private func toggleMute() {
        // Wire `UltravoxSession.toggleMicMuted()` once the iOS SDK is integrated.
        let willMute = muteButton.tintColor != .systemRed
        muteButton.tintColor = willMute ? .systemRed : .white
        muteButton.setImage(UIImage(systemName: willMute ? "mic.slash.fill" : "mic.fill"), for: .normal)
    }

    @objc private func hangUp() {
        fireCallDetails()
        teardown()
        dismiss(animated: true)
    }

    /// Idempotent — guarded by `callDetailsFired` so the user-hang-up path and
    /// the server-disconnect path don't both fire it. Uses an unstructured
    /// `Task` so the request completes after dismissal.
    private func fireCallDetails() {
        if callDetailsFired { return }
        guard let id = callId, !id.isEmpty, !sessionId.isEmpty else { return }
        callDetailsFired = true
        Task {
            do {
                _ = try await APIClient.shared.apiService.postCallDetails(
                    url: BaseAPIConstants.callDetailsURL,
                    body: CallDetailsRequest(call_id: id, session_id: sessionId)
                )
            } catch {
                CorrelationLogger.warn(message: "call-details failed", error: error)
            }
        }
    }

    private func teardown() {
        // Wire `session?.leaveCall()` once the iOS SDK is integrated.
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    private func showErrorAndFinish(_ message: String) {
        let alert = UIAlertController(title: nil, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default) { [weak self] _ in
            if self?.callId?.isEmpty == false { self?.fireCallDetails() }
            self?.dismiss(animated: true)
        })
        present(alert, animated: true)
    }

    deinit { teardown() }
}
