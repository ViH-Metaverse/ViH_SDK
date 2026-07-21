import UIKit

/// Renders a CPaaS/campaign template message (cpaas_json) — the iOS counterpart of Android's
/// `TemplateMessageViewHolder` + `item_chat_template.xml`. Supports a header image (or a tappable
/// video/doc placeholder), body text, a horizontal product carousel, template buttons and a
/// footer, all inside the received-style bubble.
public final class TemplateChatCell: UITableViewCell, UICollectionViewDataSource, UICollectionViewDelegateFlowLayout {

    // Callbacks routed to the chat screen.
    public var onTemplateButton: ((ButtonModel) -> Void)?
    public var onProductLink: ((String) -> Void)?   // Buy Now / Add to Cart url
    public var onMediaTap: ((String) -> Void)?       // header video/doc url

    private let bubble = UIView()
    private let headerImage = UIImageView()
    private let mediaOverlay = UIImageView()          // play / doc glyph over the header
    private let msgLabel = UILabel()
    private let footerLabel = UILabel()
    private let timeLabel = UILabel()
    private let buttonsStack = UIStackView()
    private let contentStack = UIStackView()
    private lazy var carousel: UICollectionView = {
        let layout = UICollectionViewFlowLayout()
        layout.scrollDirection = .horizontal
        layout.minimumLineSpacing = 10
        layout.sectionInset = UIEdgeInsets(top: 0, left: 0, bottom: 0, right: 4)
        let cv = UICollectionView(frame: .zero, collectionViewLayout: layout)
        cv.showsHorizontalScrollIndicator = false
        cv.backgroundColor = .clear
        cv.dataSource = self
        cv.delegate = self
        cv.register(ProductCardCell.self, forCellWithReuseIdentifier: "product")
        cv.translatesAutoresizingMaskIntoConstraints = false
        return cv
    }()

    private var products: [ProductModel] = []
    private var mediaURL: String?

    public override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        selectionStyle = .none
        backgroundColor = .clear
        contentView.backgroundColor = .clear

        bubble.backgroundColor = UIColor(hex: "#F7F7F7") ?? .secondarySystemBackground
        bubble.layer.cornerRadius = 18
        bubble.layer.maskedCorners = [.layerMinXMinYCorner, .layerMaxXMinYCorner, .layerMaxXMaxYCorner]
        bubble.translatesAutoresizingMaskIntoConstraints = false

        headerImage.contentMode = .scaleAspectFill
        headerImage.clipsToBounds = true
        headerImage.layer.cornerRadius = 12
        headerImage.backgroundColor = UIColor(hex: "#ECECEC")
        headerImage.isUserInteractionEnabled = true
        headerImage.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(mediaTapped)))
        headerImage.translatesAutoresizingMaskIntoConstraints = false
        headerImage.heightAnchor.constraint(equalToConstant: 150).isActive = true

        mediaOverlay.tintColor = .white
        mediaOverlay.contentMode = .center
        mediaOverlay.translatesAutoresizingMaskIntoConstraints = false

        msgLabel.numberOfLines = 0
        msgLabel.font = .systemFont(ofSize: 15)
        msgLabel.textColor = UIColor(hex: "#1C2020") ?? .label

        footerLabel.numberOfLines = 0
        footerLabel.font = .systemFont(ofSize: 12)
        footerLabel.textColor = UIColor(hex: "#828282") ?? .secondaryLabel

        timeLabel.font = .systemFont(ofSize: 10)
        timeLabel.textColor = UIColor(hex: "#828282") ?? .secondaryLabel

        buttonsStack.axis = .vertical
        buttonsStack.spacing = 6

        let carouselHeight = carousel.heightAnchor.constraint(equalToConstant: 300)
        carouselHeight.priority = .required
        carouselHeight.isActive = true

        contentStack.axis = .vertical
        contentStack.spacing = 8
        contentStack.alignment = .fill
        contentStack.translatesAutoresizingMaskIntoConstraints = false
        [headerImage, msgLabel, carousel, buttonsStack, footerLabel, timeLabel].forEach { contentStack.addArrangedSubview($0) }

        headerImage.addSubview(mediaOverlay)
        contentView.addSubview(bubble)
        bubble.addSubview(contentStack)

        NSLayoutConstraint.activate([
            bubble.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 4),
            bubble.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -4),
            bubble.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 12),
            bubble.trailingAnchor.constraint(lessThanOrEqualTo: contentView.trailingAnchor, constant: -40),
            bubble.widthAnchor.constraint(equalToConstant: 280),

            contentStack.topAnchor.constraint(equalTo: bubble.topAnchor, constant: 10),
            contentStack.bottomAnchor.constraint(equalTo: bubble.bottomAnchor, constant: -8),
            contentStack.leadingAnchor.constraint(equalTo: bubble.leadingAnchor, constant: 12),
            contentStack.trailingAnchor.constraint(equalTo: bubble.trailingAnchor, constant: -12),

            mediaOverlay.centerXAnchor.constraint(equalTo: headerImage.centerXAnchor),
            mediaOverlay.centerYAnchor.constraint(equalTo: headerImage.centerYAnchor)
        ])
    }
    public required init?(coder: NSCoder) { fatalError("not supported") }

    public override func prepareForReuse() {
        super.prepareForReuse()
        onTemplateButton = nil; onProductLink = nil; onMediaTap = nil
        buttonsStack.arrangedSubviews.forEach { $0.removeFromSuperview() }
        headerImage.image = nil
        mediaURL = nil
    }

    public func configure(with message: MessageModel) {
        let cp = message.cpaas_json
        let accent = DynamicThemeManager.shared.palette.primaryColor

        // Header media -----------------------------------------------------------------------
        if cp?.is_header_img == "1", let url = cp?.image_url, !url.isEmpty {
            headerImage.isHidden = false
            mediaOverlay.image = nil
            mediaURL = nil
            ImageLoader.load(into: headerImage, url: url, placeholderName: "placeholder")
        } else if cp?.is_header_vid == "1", let url = cp?.video_url, !url.isEmpty {
            headerImage.isHidden = false
            mediaOverlay.image = UIImage(systemName: "play.circle.fill")?.withConfiguration(UIImage.SymbolConfiguration(pointSize: 44))
            ImageLoader.load(into: headerImage, url: cp?.thumbnail ?? "", placeholderName: "placeholder")
            mediaURL = url
        } else if cp?.is_header_doc == "1", let url = cp?.doc_url, !url.isEmpty {
            headerImage.isHidden = false
            mediaOverlay.image = UIImage(systemName: "doc.fill")?.withConfiguration(UIImage.SymbolConfiguration(pointSize: 40))
            headerImage.image = nil
            mediaURL = url
        } else {
            headerImage.isHidden = true
            mediaURL = nil
        }

        // Body / footer ----------------------------------------------------------------------
        let body = cp?.msg?.replacingOccurrences(of: "<br />", with: "\n").replacingOccurrences(of: "<br>", with: "\n")
        msgLabel.text = (body?.isEmpty == false ? body : message.message)
        msgLabel.isHidden = (msgLabel.text ?? "").isEmpty
        footerLabel.text = cp?.footer
        footerLabel.isHidden = (cp?.footer ?? "").isEmpty
        timeLabel.text = TemplateChatCell.time(message.created_at)

        // Products ---------------------------------------------------------------------------
        products = cp?.product ?? []
        carousel.isHidden = products.isEmpty
        carousel.reloadData()

        // Template buttons -------------------------------------------------------------------
        buttonsStack.arrangedSubviews.forEach { $0.removeFromSuperview() }
        let buttons = cp?.button ?? []
        for b in buttons { buttonsStack.addArrangedSubview(makeButton(b, accent: accent)) }
        buttonsStack.isHidden = buttons.isEmpty
    }

    private func makeButton(_ model: ButtonModel, accent: UIColor) -> UIButton {
        let btn = UIButton(type: .system)
        btn.setTitle(model.btn_txt ?? "Open", for: .normal)
        btn.setTitleColor(accent, for: .normal)
        btn.titleLabel?.font = .systemFont(ofSize: 13, weight: .medium)
        btn.layer.borderColor = accent.cgColor
        btn.layer.borderWidth = 1
        btn.layer.cornerRadius = 14
        btn.contentEdgeInsets = UIEdgeInsets(top: 7, left: 12, bottom: 7, right: 12)
        btn.addAction(UIAction { [weak self] _ in self?.onTemplateButton?(model) }, for: .touchUpInside)
        return btn
    }

    @objc private func mediaTapped() {
        if let url = mediaURL, !url.isEmpty { onMediaTap?(url) }
    }

    // MARK: - Product carousel

    public func collectionView(_ cv: UICollectionView, numberOfItemsInSection section: Int) -> Int { products.count }

    public func collectionView(_ cv: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = cv.dequeueReusableCell(withReuseIdentifier: "product", for: indexPath) as! ProductCardCell
        cell.configure(with: products[indexPath.item]) { [weak self] url in self?.onProductLink?(url) }
        return cell
    }

    public func collectionView(_ cv: UICollectionView, layout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
        CGSize(width: 210, height: cv.bounds.height)
    }

    static func time(_ raw: String) -> String {
        guard !raw.isEmpty else { return "" }
        let p = DateFormatter(); p.locale = Locale(identifier: "en_US_POSIX")
        for fmt in ["MMM, dd yyyy HH:mm:ss", "MMM, d yyyy HH:mm:ss"] {
            p.dateFormat = fmt
            if let d = p.date(from: raw) {
                let o = DateFormatter(); o.locale = Locale(identifier: "en_US_POSIX"); o.dateFormat = "h:mm a"
                return o.string(from: d).lowercased()
            }
        }
        return ""
    }
}

/// A single product card in the template carousel (mirrors item_chat_template_product.xml).
final class ProductCardCell: UICollectionViewCell {

    private let image = UIImageView()
    private let name = UILabel()
    private let desc = UILabel()
    private let price = UILabel()
    private let buyButton = UIButton(type: .system)
    private let cartButton = UIButton(type: .system)
    private var onLink: ((String) -> Void)?
    private var buyURL: String?
    private var cartURL: String?

    override init(frame: CGRect) {
        super.init(frame: frame)
        contentView.backgroundColor = .white
        contentView.layer.cornerRadius = 14
        contentView.layer.borderWidth = 1
        contentView.layer.borderColor = UIColor(hex: "#ECECEC")?.cgColor
        contentView.layer.masksToBounds = true

        image.contentMode = .scaleAspectFill
        image.clipsToBounds = true
        image.backgroundColor = UIColor(hex: "#F0F0F0")
        image.translatesAutoresizingMaskIntoConstraints = false

        name.font = .systemFont(ofSize: 13, weight: .semibold)
        name.textColor = UIColor(hex: "#1C2020") ?? .label
        name.numberOfLines = 1
        desc.font = .systemFont(ofSize: 11)
        desc.textColor = UIColor(hex: "#828282") ?? .secondaryLabel
        desc.numberOfLines = 2
        price.font = .systemFont(ofSize: 13, weight: .bold)
        price.textColor = UIColor(hex: "#1C2020") ?? .label

        let accent = DynamicThemeManager.shared.palette.primaryColor
        for (b, title) in [(buyButton, "Buy Now"), (cartButton, "Add to Cart")] {
            b.setTitle(title, for: .normal)
            b.setTitleColor(accent, for: .normal)
            b.titleLabel?.font = .systemFont(ofSize: 11, weight: .medium)
            b.layer.borderColor = accent.cgColor
            b.layer.borderWidth = 1
            b.layer.cornerRadius = 12
        }
        buyButton.addAction(UIAction { [weak self] _ in if let u = self?.buyURL { self?.onLink?(u) } }, for: .touchUpInside)
        cartButton.addAction(UIAction { [weak self] _ in if let u = self?.cartURL { self?.onLink?(u) } }, for: .touchUpInside)

        let text = UIStackView(arrangedSubviews: [name, desc, price])
        text.axis = .vertical; text.spacing = 3
        let buttons = UIStackView(arrangedSubviews: [buyButton, cartButton])
        buttons.axis = .horizontal; buttons.distribution = .fillEqually; buttons.spacing = 6

        let stack = UIStackView(arrangedSubviews: [image, text, buttons])
        stack.axis = .vertical; stack.spacing = 6
        stack.translatesAutoresizingMaskIntoConstraints = false
        stack.isLayoutMarginsRelativeArrangement = true
        stack.layoutMargins = UIEdgeInsets(top: 8, left: 8, bottom: 8, right: 8)
        stack.setCustomSpacing(8, after: image)
        contentView.addSubview(stack)

        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: contentView.topAnchor),
            stack.bottomAnchor.constraint(equalTo: contentView.bottomAnchor),
            stack.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            image.heightAnchor.constraint(equalToConstant: 150),
            buyButton.heightAnchor.constraint(equalToConstant: 30)
        ])
    }
    required init?(coder: NSCoder) { fatalError("not supported") }

    override func prepareForReuse() {
        super.prepareForReuse()
        image.image = nil; onLink = nil; buyURL = nil; cartURL = nil
    }

    func configure(with product: ProductModel, onLink: @escaping (String) -> Void) {
        self.onLink = onLink
        name.text = product.prod_nm
        desc.text = product.prod_dsc
        price.text = product.prod_prc
        buyURL = product.buynw_url
        cartURL = product.addttocrt_url
        buyButton.isHidden = (product.buynw_url ?? "").isEmpty
        cartButton.isHidden = (product.addttocrt_url ?? "").isEmpty
        ImageLoader.load(into: image, url: product.img_url ?? "", placeholderName: "placeholder")
    }
}
