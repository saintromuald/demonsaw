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

#include <QCoreApplication>
#include <QDesktopServices>
#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QProcess>
#include <QStandardPaths>
#include <QUrl>
#include <QTemporaryDir>

#include "system/type.h"
#include "utility/value.h"
#include "utility/version.h"
#include "widget/update_checker.h"

namespace eja
{
	namespace
	{
		const char* releases_url = "https://api.github.com/repos/saintromuald/demonsaw/releases/latest";

		bool matches(const QString& name, const char* token)
		{
			return name.contains(QLatin1String(token), Qt::CaseInsensitive);
		}
	}

	// Interface
	update_checker::update_checker(QObject* parent /* = nullptr */) : QObject(parent)
	{
		m_network = new QNetworkAccessManager(this);
	}

	update_checker::~update_checker()
	{ }

	// Utility
	bool update_checker::install_dir_writable() const
	{
		const auto dir = QFileInfo(QCoreApplication::applicationFilePath()).dir();
		return dir.exists() && QFileInfo(dir.absolutePath()).isWritable();
	}

	bool update_checker::can_install() const
	{
		if (m_asset_url.isEmpty())
			return false;

#if _MACX
		return m_asset_kind == "dmg";
#elif _WIN32
		return m_asset_kind == "installer";
#else
		return (m_asset_kind == "tarball" || m_asset_kind == "appimage") && install_dir_writable();
#endif
	}

	// Check
	void update_checker::check(const bool interactive)
	{
		m_interactive = interactive;

		const QUrl url(QString::fromLatin1(releases_url));
		QNetworkRequest request(url);
		request.setHeader(QNetworkRequest::UserAgentHeader, QString("Demonsaw/%1").arg(software::version));
		request.setRawHeader("Accept", "application/vnd.github+json");

		const auto reply = m_network->get(request);
		connect(reply, &QNetworkReply::finished, this, [&, reply]() { on_release(reply); });
	}

	void update_checker::on_release(QNetworkReply* reply)
	{
		reply->deleteLater();

		if (reply->error() != QNetworkReply::NoError)
		{
			emit failed(reply->errorString());
			return;
		}

		const auto doc = QJsonDocument::fromJson(reply->readAll());
		const auto root = doc.object();
		const auto current = QString::fromLatin1(software::version);

		QString tag = root.value("tag_name").toString();
		if (tag.startsWith('v') || tag.startsWith('V'))
			tag.remove(0, 1);

		m_page_url = root.value("html_url").toString();
		m_notes = root.value("body").toString();

		// Platform asset
		const auto assets = root.value("assets").toArray();
		for (const auto& value : assets)
		{
			const auto asset = value.toObject();
			const auto name = asset.value("name").toString();

#if _MACX
			if (name.endsWith(".dmg", Qt::CaseInsensitive))
				m_asset_kind = "dmg";
#elif _WIN32
			if (name.endsWith(".exe", Qt::CaseInsensitive) || name.endsWith(".msi", Qt::CaseInsensitive))
				m_asset_kind = "installer";
#else
			if (name.endsWith(".tar.gz", Qt::CaseInsensitive) || name.endsWith(".tgz", Qt::CaseInsensitive))
				m_asset_kind = "tarball";
			else if (name.endsWith(".AppImage", Qt::CaseInsensitive))
				m_asset_kind = "appimage";
#endif

			if (!m_asset_kind.isEmpty())
			{
				m_asset_name = name;
				m_asset_url = asset.value("browser_download_url").toString();
				break;
			}
		}

		// Version decision
		const auto cmp = version_compare(tag.toStdString(), current.toStdString());
		const auto newer = cmp > 0;

		if (!newer)
		{
			emit up_to_date(current);
			return;
		}

		m_version = tag;

		if (m_asset_kind.isEmpty())
		{
			// A newer release exists but has no package for this platform
			// (e.g. an Android-only release).
			emit no_package(m_version, m_notes);
			return;
		}

		emit update_available(m_version, m_notes, can_install());
	}

	// Install
	void update_checker::download_and_install()
	{
		if (m_asset_url.isEmpty())
			return;

		const QUrl url(m_asset_url);
		QNetworkRequest request(url);
		request.setHeader(QNetworkRequest::UserAgentHeader, QString("Demonsaw/%1").arg(software::version));

		const auto reply = m_network->get(request);
		connect(reply, &QNetworkReply::finished, this, [&, reply]() { on_download(reply); });
	}

	void update_checker::on_download(QNetworkReply* reply)
	{
		reply->deleteLater();

		if (reply->error() != QNetworkReply::NoError)
		{
			emit failed(reply->errorString());
			return;
		}

		const auto bytes = reply->readAll();
		const auto temp = QDir::temp().filePath(m_asset_name);
		QFile file(temp);
		if (!file.open(QFile::WriteOnly))
		{
			emit failed(QString("Could not write %1").arg(temp));
			return;
		}
		file.write(bytes);
		file.close();

#if _MACX
		// Open the DMG; the user drags the app into /Applications.
		QDesktopServices::openUrl(QUrl::fromLocalFile(temp));
#elif _WIN32
		// Run the installer, then quit.
		QProcess::startDetached(temp, QStringList());
		qApp->quit();
#else
		if (m_asset_kind == "appimage")
		{
			QFileInfo info(temp);
			QFile::setPermissions(temp, info.permissions() | QFile::ExeUser | QFile::ExeOwner);
			replace_and_relaunch(temp);
		}
		else
		{
			// Extract the tarball and find the executable inside.
			QTemporaryDir dir;
			if (!dir.isValid())
			{
				emit failed("Could not create a temporary directory");
				return;
			}

			QProcess tar;
			tar.start("tar", QStringList() << "-xzf" << temp << "-C" << dir.path());
			tar.waitForFinished(30000);

			const auto files = QDir(dir.path()).entryList(QDir::Files | QDir::Executable, QDir::Name);
			QString binary;
			for (const auto& f : files)
			{
				const auto lower = f.toLower();
				if (lower.contains("demonsaw"))
				{
					binary = dir.path() + "/" + f;
					break;
				}
			}

			if (binary.isEmpty())
			{
				emit failed("Could not find the executable in the release archive");
				return;
			}

			replace_and_relaunch(binary);
		}
#endif
	}

	void update_checker::replace_and_relaunch(const QString& binary)
	{
		const auto target = QCoreApplication::applicationFilePath();

		// Move the new binary into place after this process exits.
		const auto script = QDir::temp().filePath("demonsaw-update.sh");
		QFile sh(script);
		if (!sh.open(QFile::WriteOnly))
		{
			emit failed("Could not write the update script");
			return;
		}

		QString body;
		body += "#!/bin/sh\n";
		body += "sleep 2\n";
		body += QString("mv -f \"%1\" \"%2\" || exit 1\n").arg(binary, target);
		body += QString("chmod +x \"%1\"\n").arg(target);
		body += QString("(nohup \"%1\" >/dev/null 2>&1 &)\n").arg(target);
		body += QString("rm -f \"%1\"\n").arg(script);
		sh.write(body.toUtf8());
		sh.close();

		QFile::setPermissions(script, QFile::permissions(script) | QFile::ExeUser | QFile::ExeOwner);

		QProcess::startDetached("/bin/sh", QStringList() << script);
		qApp->quit();
	}

	void update_checker::open_release_page() const
	{
		if (!m_page_url.isEmpty())
			QDesktopServices::openUrl(QUrl(m_page_url));
	}
}
