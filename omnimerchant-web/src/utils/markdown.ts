import DOMPurify from 'dompurify'
import { marked } from 'marked'

const ALLOWED_MARKDOWN_TAGS = [
  'a',
  'blockquote',
  'br',
  'code',
  'em',
  'h1',
  'h2',
  'h3',
  'h4',
  'h5',
  'h6',
  'hr',
  'li',
  'ol',
  'p',
  'pre',
  'strong',
  'table',
  'tbody',
  'td',
  'th',
  'thead',
  'tr',
  'ul',
]

export function renderSafeMarkdown(text: string): string {
  const html = marked.parse(text || '', {
    breaks: true,
    async: false,
  }) as string

  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ALLOWED_MARKDOWN_TAGS,
    ALLOWED_ATTR: ['href', 'title'],
    ALLOW_DATA_ATTR: false,
    FORBID_TAGS: ['style', 'script', 'iframe', 'object', 'embed', 'form', 'input', 'button', 'textarea', 'select', 'svg', 'math'],
    FORBID_ATTR: ['style', 'src', 'srcdoc'],
  })
}
