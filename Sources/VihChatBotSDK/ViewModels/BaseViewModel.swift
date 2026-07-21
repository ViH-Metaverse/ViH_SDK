import Foundation
import Combine

/// Mirrors `viewmodel/BaseViewModel.kt`. Android `viewModelScope` becomes a
/// `Task` set; cancel-all on deinit replicates `onCleared`.
open class BaseViewModel {

    public static let TAG = "BaseViewModel"

    /// Tasks the VM launched. Cleared on deinit; mirrors Android `viewModelScope`.
    private var tasks: [Task<Void, Never>] = []

    public init() {}

    deinit {
        tasks.forEach { $0.cancel() }
    }

    /// Launch an async block on a child task and track it for cancellation.
    @discardableResult
    public func launch(_ block: @escaping () async -> Void) -> Task<Void, Never> {
        let task = Task { await block() }
        tasks.append(task)
        return task
    }
}

/// Replacement for Android `MutableLiveData`. Subscribers get the current value
/// on subscribe (like LiveData's "observe sticky on attach"). Updates always
/// hop to the main queue so callers can mutate UI directly.
public final class LiveData<Value> {

    private let subject: CurrentValueSubject<Value?, Never>

    public init(_ initial: Value? = nil) {
        self.subject = CurrentValueSubject(initial)
    }

    public var value: Value? { subject.value }

    public func postValue(_ value: Value) {
        if Thread.isMainThread {
            subject.send(value)
        } else {
            DispatchQueue.main.async { self.subject.send(value) }
        }
    }

    public func setValue(_ value: Value) {
        precondition(Thread.isMainThread, "setValue must be called from the main thread")
        subject.send(value)
    }

    public func observe(_ block: @escaping (Value) -> Void) -> AnyCancellable {
        subject.receive(on: DispatchQueue.main).sink { value in
            if let value = value { block(value) }
        }
    }
}

/// Mirrors `viewmodel/SingleLiveEvent.kt` — fires once per setValue and ignores
/// the sticky replay LiveData normally gives. Useful for navigation/error events.
public final class SingleLiveEvent<Value> {

    private let subject = PassthroughSubject<Value, Never>()

    public init() {}

    public func postValue(_ value: Value) {
        if Thread.isMainThread {
            subject.send(value)
        } else {
            DispatchQueue.main.async { self.subject.send(value) }
        }
    }

    public func observe(_ block: @escaping (Value) -> Void) -> AnyCancellable {
        subject.receive(on: DispatchQueue.main).sink(receiveValue: block)
    }
}
