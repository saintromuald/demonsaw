//
// The MIT License(MIT)
//
// Copyright(c) 2014 Demonsaw LLC
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

#ifndef _EJA_UPDATE_CHECKER_H_
#define _EJA_UPDATE_CHECKER_H_

#include <QObject>
#include <QString>

class QNetworkAccessManager;
class QNetworkReply;

namespace eja
{
	// Checks the fork's GitHub releases for a newer version and, on Linux,
	// can replace the running binary and relaunch it (auto-update). On
	// macOS the package is a DMG the user opens; on Windows an installer.
	class update_checker final : public QObject
	{
		Q_OBJECT;

	private:
		QNetworkAccessManager* m_network = nullptr;
		bool m_interactive = false;

		QString m_version;
		QString m_notes;
		QString m_page_url;
		QString m_asset_url;
		QString m_asset_name;
		QString m_asset_kind; // "tarball", "appimage", "dmg", "installer"

		// Utility
		bool can_install() const;
		bool install_dir_writable() const;
		void replace_and_relaunch(const QString& binary);

	public:
		update_checker(QObject* parent = nullptr);
		virtual ~update_checker() override;

		// Check
		void check(const bool interactive);

		// Install
		void download_and_install();
		void open_release_page() const;

	signals:
		void up_to_date(const QString& version);
		void update_available(const QString& version, const QString& notes, const bool can_install);
		void no_package(const QString& version, const QString& notes);
		void failed(const QString& error);

	private slots:
		void on_release(QNetworkReply* reply);
		void on_download(QNetworkReply* reply);
	};
}

#endif
