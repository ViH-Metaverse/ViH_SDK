import Foundation

/// Mirrors `listener/onItemChatClickListener.kt`.
public protocol OnItemChatClickListener: AnyObject {
    func onItemClick(item: String, sessionId: String)
    func onChatButtonClick(position: Int, model: ButtonModel)
    func onImageClick(imageUrl: String)
    func onVideoClick(videoFileURL: URL)
}

/// Mirrors `listener/OnChatTemplateButtonRvItemClickListener.kt`.
public protocol OnChatTemplateButtonClickListener: AnyObject {
    func onTemplateButtonClick(position: Int, model: ButtonModel)
}

/// Mirrors `listener/OnDiscoverRvItemClickListener.kt`.
public protocol OnDiscoverItemClickListener: AnyObject {
    func onDiscoverItemClick(position: Int, model: EnterPriseModel)
}
