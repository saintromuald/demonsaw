import SwiftUI

// Port of ConnectScreen()
struct ConnectView: View {
    let error: String?
    let onConnect: (String, Int, String, String) -> Void

    @State private var host: String
    @State private var port: String
    @State private var name: String
    @State private var passphrase: String

    private static let defaults = UserDefaults.standard

    init(error: String?, onConnect: @escaping (String, Int, String, String) -> Void) {
        self.error = error
        self.onConnect = onConnect
        _host = State(initialValue: Self.defaults.string(forKey: "host") ?? "router.demonsaw.com")
        _port = State(initialValue: String(Self.defaults.integer(forKey: "port") == 0 ? 80 : Self.defaults.integer(forKey: "port")))
        _name = State(initialValue: Self.defaults.string(forKey: "name") ?? "iOS")
        _passphrase = State(initialValue: Self.defaults.string(forKey: "passphrase") ?? "")
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                Spacer().frame(height: 32)

                Text("demonsaw")
                    .font(.system(size: 40, weight: .black))
                    .foregroundColor(DsTheme.onBackground)
                Text("Believe in the Right to Share")
                    .font(.system(size: 13))
                    .foregroundColor(DsTheme.onSurfaceVariant)

                Spacer().frame(height: 36)

                VStack(spacing: 12) {
                    field("Name", text: $name, limit: 16)
                    field("Router address", text: $host)
                    field("Port", text: $port, digitsOnly: true, maxLength: 5)
                        .keyboardType(.numberPad)
                    field("Group passphrase", text: $passphrase, secure: true)
                    Text("Everyone who enters the same passphrase joins your community.")
                        .font(.system(size: 12))
                        .foregroundColor(DsTheme.onSurfaceVariant)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.leading, 4)
                }

                if let error, !error.isEmpty {
                    Text(error)
                        .font(.system(size: 13))
                        .foregroundColor(DsTheme.error)
                        .padding(.top, 12)
                }

                Spacer().frame(height: 24)

                Button(action: connect) {
                    Text("Start")
                        .font(.system(size: 16, weight: .semibold))
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(buttonEnabled ? DsTheme.blue : DsTheme.blue.opacity(0.4))
                        .foregroundColor(.white)
                        .cornerRadius(10)
                }
                .disabled(!buttonEnabled)

                Spacer().frame(height: 32)

                Text("Demonsaw 4 for iOS — free and open source, MIT licensed.")
                    .font(.system(size: 11))
                    .multilineTextAlignment(.center)
                    .foregroundColor(DsTheme.onSurfaceVariant)
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 8)
        }
        .background(DsTheme.background)
    }

    private var buttonEnabled: Bool {
        !host.trimmingCharacters(in: .whitespaces).isEmpty && !port.isEmpty
    }

    @ViewBuilder
    private func field(
        _ label: String,
        text: Binding<String>,
        limit: Int = 64,
        digitsOnly: Bool = false,
        maxLength: Int = 0,
        secure: Bool = false
    ) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 12))
                .foregroundColor(DsTheme.onSurfaceVariant)
            Group {
                if secure {
                    SecureField("", text: text)
                } else {
                    TextField("", text: text)
                }
            }
            .textFieldStyle(PlainTextFieldStyle())
            .autocorrectionDisabled()
            .textInputAutocapitalization(secure ? .never : .sentences)
            .padding(12)
            .background(DsTheme.surface)
            .cornerRadius(8)
            .onChange(of: text.wrappedValue) { newValue in
                var value = newValue
                if digitsOnly { value = value.filter(\.isNumber) }
                if maxLength > 0 && value.count > maxLength { value = String(value.prefix(maxLength)) }
                if value.count > limit { value = String(value.prefix(limit)) }
                if value != newValue { text.wrappedValue = value }
            }
        }
    }

    private func connect() {
        let parsedPort = Int(port).map { min(max($0, 1), 65535) } ?? 1337
        let trimmedHost = host.trimmingCharacters(in: .whitespaces)
        let trimmedName = name.trimmingCharacters(in: .whitespaces)

        let defaults = Self.defaults
        defaults.set(trimmedHost, forKey: "host")
        defaults.set(parsedPort, forKey: "port")
        defaults.set(trimmedName, forKey: "name")
        defaults.set(passphrase, forKey: "passphrase")

        onConnect(trimmedHost, parsedPort, trimmedName, passphrase)
    }
}
