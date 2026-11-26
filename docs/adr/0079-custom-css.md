# 79. Custom CSS is one stylesheet, important, offline and local

Status: proposed

## Context

Readers coming from Readest and KOReader keep a short stylesheet of their
own: drop caps, small capitals on a chapter's first line, titles in the
text colour. Liseur's typography controls cover size, spacing, margins,
alignment and weight, but not the shape of a page, and a control per
taste does not scale.

Three things make a stylesheet harder than it looks here. Readium's user
settings and many publishers' rules are `!important`, so a plain rule
often loses without a sign. A stylesheet can fetch: `@import`, `url()`,
`image-set()`, and Liseur promises to reach only the services the reader
chose. And Readium decides whether to link its own default stylesheet by
looking for `<style` in a resource, so nothing can be written into the
served file (see `WideContentFit`).

## Decision

One stylesheet for every book, with a switch to turn it off without
losing it. There is no per-book stylesheet. It is edited under Settings >
Reading appearance > Advanced > Custom CSS, and from the reader's own
Advanced sheet, which opens the same editor full screen over the book:
edits save as they are typed, so the page is restyled when it closes.

`domain/CustomCss.kt` prepares the text before any page sees it:

- Every declaration gets `!important`, except inside descriptor blocks
  (`@font-face`, `@keyframes`, `@counter-style`, `@property` and the
  like), where it would make the browser drop the line.
- `@import` and every `url()`, `src()` or `image-set()` that is not a
  `data:` URL or a `#fragment` is left out, read with CSS escapes decoded
  and comments removed the way the tokenizer removes them. The editor says
  how many rules were left out. Relative URLs go too: they would resolve
  inside whichever book is open.
- The finished text is checked once more as a whole, and nothing is
  applied if anything that fetches survived.

`reader/CustomStylesheet.kt` puts the result on the page as an adopted
constructed stylesheet, from `repairPage` in `ReaderScreen`: first among
the repairs, on every resource as it loads, and through the preference
reflow (anchor captured, layout generation advanced, place restored) when
the stylesheet changes while a book is open. Adopted sheets cascade after
every linked sheet, so the reader wins ties, and a book's
Content-Security-Policy does not apply to them. A web view without them
gets a `<style>` instead, which a strict policy can refuse.

The stylesheet is stored in the reader preferences under `custom_css` and
`custom_css_on`, and is neither in `READER_BACKUP_TYPES` nor in
`syncableSettings`: it stays on the device it was written on.

The editor warns when a rule sets `color`, `background` or
`background-color` to anything but a theme-following value. Liseur hands
Readium a text and background colour for every theme, and ReadiumCSS then
forces `color: inherit` on everything but headings and `pre`, and a
transparent background on every element, with selectors no ordinary rule
outranks. So colour rules mostly do nothing; heading colours apply in
Light only, and `:root[style*="readium-night-on"]` reaches the dark
themes. The dark-theme snippet and the warning both name that selector.

Ready-made snippets (`domain/CustomCssSnippet.kt`) are appended under a
comment with their title, and are shown as added when the stylesheet
holds them line for line.

## Consequences

A stylesheet change reflows the open book like a typography change does.
Each chapter is styled as it becomes the visible resource, like the other
repairs, so a neighbouring chapter preloaded off screen can move once
when it is turned to.

A rule that loads a picture or font from a file does nothing. Fonts and
images embedded as `data:` URLs work.

Only reflowable books are styled; fixed-layout pages are left as drawn.
