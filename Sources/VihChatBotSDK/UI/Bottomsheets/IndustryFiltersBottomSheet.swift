import UIKit

/// Mirrors `ui/bottomsheet/IndustryFiltersBottomSheetFragment.kt`. Modal sheet
/// that lets users multi-select industry filters. Uses iOS sheet detents.
public final class IndustryFiltersBottomSheet: UIViewController, UITableViewDataSource, UITableViewDelegate {

    public var industries: [String] = []
    public var selected: Set<String> = []
    public var onApply: ((Set<String>) -> Void)?

    private let tableView = UITableView()
    private let applyButton = UIButton(type: .system)

    public override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        if let sheet = sheetPresentationController {
            sheet.detents = [.medium(), .large()]
        }

        tableView.translatesAutoresizingMaskIntoConstraints = false
        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "cell")

        applyButton.setTitle("Apply", for: .normal)
        applyButton.addTarget(self, action: #selector(applyTapped), for: .touchUpInside)
        applyButton.translatesAutoresizingMaskIntoConstraints = false

        view.addSubview(tableView)
        view.addSubview(applyButton)
        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: applyButton.topAnchor, constant: -8),

            applyButton.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            applyButton.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -12)
        ])
    }

    public func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        industries.count
    }

    public func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "cell", for: indexPath)
        let industry = industries[indexPath.row]
        cell.textLabel?.text = industry
        cell.accessoryType = selected.contains(industry) ? .checkmark : .none
        return cell
    }

    public func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        let industry = industries[indexPath.row]
        if selected.contains(industry) { selected.remove(industry) } else { selected.insert(industry) }
        tableView.reloadRows(at: [indexPath], with: .none)
        tableView.deselectRow(at: indexPath, animated: true)
    }

    @objc private func applyTapped() {
        onApply?(selected)
        dismiss(animated: true)
    }
}
