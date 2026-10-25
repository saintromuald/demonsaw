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

#ifndef _EJA_VERSION_H_
#define _EJA_VERSION_H_

#include <string>
#include <vector>

namespace eja
{
	// Compare dotted numeric versions ("4.2.1" vs "4.20"). Everything but
	// digits is a separator, so tags like "v4.2.1-android.1" compare as
	// 4.2.1.1. Missing segments count as zero. Returns -1, 0, or 1.
	inline int version_compare(const std::string& lhs, const std::string& rhs)
	{
		const auto nums = [](const std::string& s)
		{
			std::vector<size_t> out;
			size_t value = 0;
			bool have = false;

			for (const char c : s)
			{
				if (c >= '0' && c <= '9')
				{
					value = value * 10 + static_cast<size_t>(c - '0');
					have = true;
				}
				else if (have)
				{
					out.push_back(value);
					value = 0;
					have = false;
				}
			}

			if (have)
				out.push_back(value);

			return out;
		};

		const auto a = nums(lhs);
		const auto b = nums(rhs);
		const auto n = a.size() > b.size() ? a.size() : b.size();

		for (size_t i = 0; i < n; ++i)
		{
			const auto x = i < a.size() ? a[i] : 0;
			const auto y = i < b.size() ? b[i] : 0;

			if (x < y)
				return -1;
			if (x > y)
				return 1;
		}

		return 0;
	}
}

#endif
