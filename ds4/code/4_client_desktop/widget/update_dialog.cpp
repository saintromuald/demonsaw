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

#include <QDialogButtonBox>
#include <QHBoxLayout>
#include <QLabel>
#include <QPushButton>
#include <QTextEdit>
#include <QVBoxLayout>

#include "widget/update_dialog.h"

namespace eja
{
	update_dialog::update_dialog(const QString& title, const QString& text, const bool can_install, QWidget* parent /* = nullptr */) : QDialog(parent)
	{
		setObjectName("dialog");
		setWindowTitle(title);

		const auto layout = new QVBoxLayout(this);
		layout->setMargin(16);
		layout->setSpacing(12);

		auto label = new QLabel(title, this);
		QFont font = label->font();
		font.setPointSize(font.pointSize() + 2);
		font.setBold(true);
		label->setFont(font);
		layout->addWidget(label);

		auto body = new QTextEdit(this);
		body->setReadOnly(true);
		body->setPlainText(text);
		body->setMinimumSize(480, 260);
		layout->addWidget(body);

		const auto hlayout = new QHBoxLayout();
		hlayout->addStretch(1);

		m_later = new QPushButton("Later", this);
		hlayout->addWidget(m_later);

		m_download = new QPushButton("Download from GitHub", this);
		m_download->setDefault(!can_install);
		hlayout->addWidget(m_download);

		if (can_install)
		{
			m_install = new QPushButton("Install & Restart", this);
			m_install->setDefault(true);
			hlayout->addWidget(m_install);
		}

		layout->addLayout(hlayout);

		connect(m_later, &QPushButton::clicked, this, &QDialog::reject);
		connect(m_download, &QPushButton::clicked, this, [&]() { emit download(); reject(); });
		if (m_install)
			connect(m_install, &QPushButton::clicked, this, [&]() { emit install(); accept(); });
	}
}
