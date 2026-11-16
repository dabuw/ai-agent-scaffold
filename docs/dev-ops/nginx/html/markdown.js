/**
 * 轻量 Markdown 渲染（无第三方依赖）
 * 支持：标题、粗体/斜体、行内代码、围栏代码块（含简易高亮）、无序/有序列表、引用、分割线、表格、链接。
 * 所有输出都先做 HTML 转义，避免模型输出里的脚本被注入执行。
 */
(function () {
  'use strict';

  var KEYWORDS = {
    java: ['public', 'private', 'protected', 'class', 'interface', 'enum', 'extends', 'implements', 'static', 'final', 'void', 'new', 'return', 'if', 'else', 'for', 'while', 'try', 'catch', 'finally', 'throw', 'throws', 'import', 'package', 'null', 'true', 'false', 'this', 'super', 'int', 'long', 'double', 'boolean', 'String'],
    js: ['const', 'let', 'var', 'function', 'return', 'if', 'else', 'for', 'while', 'async', 'await', 'new', 'class', 'extends', 'import', 'export', 'from', 'null', 'undefined', 'true', 'false', 'this', 'typeof'],
    ts: ['const', 'let', 'var', 'function', 'return', 'if', 'else', 'for', 'while', 'async', 'await', 'new', 'class', 'interface', 'type', 'enum', 'import', 'export', 'from', 'null', 'undefined', 'true', 'false'],
    python: ['def', 'class', 'return', 'if', 'elif', 'else', 'for', 'while', 'import', 'from', 'as', 'try', 'except', 'finally', 'with', 'lambda', 'None', 'True', 'False', 'self', 'yield', 'in', 'not', 'and', 'or'],
    sql: ['select', 'from', 'where', 'insert', 'into', 'values', 'update', 'set', 'delete', 'join', 'left', 'right', 'inner', 'group', 'by', 'order', 'limit', 'create', 'table', 'index', 'primary', 'key', 'and', 'or', 'as', 'on'],
    json: ['true', 'false', 'null'],
    bash: ['if', 'then', 'else', 'fi', 'for', 'do', 'done', 'while', 'case', 'esac', 'function', 'return', 'export', 'local', 'echo', 'cd']
  };

  function escapeHtml(text) {
    return String(text).replace(/[&<>"']/g, function (ch) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch];
    });
  }

  function highlight(code, lang) {
    var key = String(lang || '').toLowerCase();
    if (key === 'js' || key === 'javascript') key = 'js';
    if (key === 'sh' || key === 'shell' || key === 'zsh') key = 'bash';
    if (key === 'py') key = 'python';
    if (key === 'ts') key = 'ts';

    var words = KEYWORDS[key] || [];
    var keywordPart = words.length ? words.join('|') : '(?!)';
    var pattern = new RegExp(
      '(\\/\\/[^\\n]*|\\/\\*[\\s\\S]*?\\*\\/|#[^\\n]*)' +
      '|("(?:[^"\\\\]|\\\\.)*"|\'(?:[^\'\\\\]|\\\\.)*\')' +
      '|\\b(' + keywordPart + ')\\b' +
      '|\\b(\\d+(?:\\.\\d+)?)\\b',
      'g'
    );

    var result = '';
    var lastIndex = 0;
    var match;
    while ((match = pattern.exec(code)) !== null) {
      result += escapeHtml(code.slice(lastIndex, match.index));
      var cls = match[1] ? 'tok-comment' : match[2] ? 'tok-string' : match[3] ? 'tok-keyword' : 'tok-number';
      result += '<span class="' + cls + '">' + escapeHtml(match[0]) + '</span>';
      lastIndex = match.index + match[0].length;
    }
    result += escapeHtml(code.slice(lastIndex));
    return result;
  }

  function inline(text) {
    var codes = [];
    var out = escapeHtml(text).replace(/`([^`]+)`/g, function (_, code) {
      codes.push(code);
      return '\u0000' + (codes.length - 1) + '\u0000';
    });

    out = out
      .replace(/!\[([^\]]*)\]\(([^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>')
      .replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>')
      .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
      .replace(/(^|[^*])\*([^*\n]+)\*/g, '$1<em>$2</em>')
      .replace(/~~([^~]+)~~/g, '<del>$1</del>');

    out = out.replace(/\u0000(\d+)\u0000/g, function (_, idx) {
      return '<code class="inline-code">' + codeEscape(codes[Number(idx)]) + '</code>';
    });

    return out;
  }

  function codeEscape(code) {
    return escapeHtml(code);
  }

  function isTableSeparator(line) {
    return /^\s*\|?[\s:-]*-[-\s:|]*\|?\s*$/.test(line) && line.indexOf('-') !== -1;
  }

  function splitRow(line) {
    return line.replace(/^\s*\|/, '').replace(/\|\s*$/, '').split('|').map(function (cell) {
      return cell.trim();
    });
  }

  function render(markdown) {
    var lines = String(markdown == null ? '' : markdown).replace(/\r\n?/g, '\n').split('\n');
    var html = [];
    var i = 0;

    while (i < lines.length) {
      var line = lines[i];

      // 围栏代码块
      var fence = line.match(/^\s*```\s*([\w+-]*)\s*$/);
      if (fence) {
        var lang = fence[1] || '';
        var buffer = [];
        i++;
        while (i < lines.length && !/^\s*```\s*$/.test(lines[i])) {
          buffer.push(lines[i]);
          i++;
        }
        i++;
        html.push('<pre class="code-block"><code>' + highlight(buffer.join('\n'), lang) + '</code></pre>');
        continue;
      }

      // 空行
      if (!line.trim()) { i++; continue; }

      // 分割线
      if (/^\s*(-{3,}|\*{3,}|_{3,})\s*$/.test(line)) {
        html.push('<hr>');
        i++;
        continue;
      }

      // 标题
      var heading = line.match(/^(#{1,6})\s+(.*)$/);
      if (heading) {
        var level = Math.min(heading[1].length + 1, 6);
        html.push('<h' + level + '>' + inline(heading[2].trim()) + '</h' + level + '>');
        i++;
        continue;
      }

      // 表格
      if (line.indexOf('|') !== -1 && i + 1 < lines.length && isTableSeparator(lines[i + 1])) {
        var header = splitRow(line);
        i += 2;
        var rows = [];
        while (i < lines.length && lines[i].indexOf('|') !== -1 && lines[i].trim()) {
          rows.push(splitRow(lines[i]));
          i++;
        }
        var table = '<div class="table-wrap"><table><thead><tr>';
        header.forEach(function (cell) { table += '<th>' + inline(cell) + '</th>'; });
        table += '</tr></thead><tbody>';
        rows.forEach(function (row) {
          table += '<tr>';
          header.forEach(function (_, idx) { table += '<td>' + inline(row[idx] || '') + '</td>'; });
          table += '</tr>';
        });
        table += '</tbody></table></div>';
        html.push(table);
        continue;
      }

      // 引用
      if (/^\s*>\s?/.test(line)) {
        var quote = [];
        while (i < lines.length && /^\s*>\s?/.test(lines[i])) {
          quote.push(lines[i].replace(/^\s*>\s?/, ''));
          i++;
        }
        html.push('<blockquote>' + render(quote.join('\n')) + '</blockquote>');
        continue;
      }

      // 有序列表
      if (/^\s*\d+\.\s+/.test(line)) {
        var oitems = [];
        while (i < lines.length && /^\s*\d+\.\s+/.test(lines[i])) {
          oitems.push(lines[i].replace(/^\s*\d+\.\s+/, ''));
          i++;
        }
        html.push('<ol>' + oitems.map(function (item) { return '<li>' + inline(item) + '</li>'; }).join('') + '</ol>');
        continue;
      }

      // 无序列表
      if (/^\s*[-*+]\s+/.test(line)) {
        var uitems = [];
        while (i < lines.length && /^\s*[-*+]\s+/.test(lines[i])) {
          uitems.push(lines[i].replace(/^\s*[-*+]\s+/, ''));
          i++;
        }
        html.push('<ul>' + uitems.map(function (item) { return '<li>' + inline(item) + '</li>'; }).join('') + '</ul>');
        continue;
      }

      // 段落（连续非空行合并，保留软换行）
      var paragraph = [];
      while (i < lines.length && lines[i].trim() && !/^\s*(```|#{1,6}\s|>|\d+\.\s|[-*+]\s)/.test(lines[i])) {
        paragraph.push(lines[i]);
        i++;
        if (i < lines.length && lines[i].indexOf('|') !== -1 && i + 1 < lines.length && isTableSeparator(lines[i + 1])) {
          break;
        }
      }
      html.push('<p>' + paragraph.map(inline).join('<br>') + '</p>');
    }

    return html.join('');
  }

  window.AgentMarkdown = { render: render, highlight: highlight, escapeHtml: escapeHtml };
}());
