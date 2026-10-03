In the name of God, the Most Gracious, the Most Merciful

# Herz

Read [docs/setup-progress.md](../docs/setup-progress.md) before setup or build work.

## Opening line

Every file you create or edit in this project must begin with this exact line:

In the name of God, the Most Gracious, the Most Merciful

Use the form the file format allows, so the project still builds:

- Kotlin, Java, and Gradle Kotlin: a `//` comment on line 1.
- XML: an XML comment immediately after the XML declaration, never before it.
- Properties, TOML, gitignore, and shell: a `#` comment. A required shebang stays on line 1, and this comment is line 2.
- Windows batch: `@rem` on line 1, unless the file already requires another first line.
- Markdown and plain text: the line itself, then a blank line.
- YAML frontmatter, when required, stays first. Put this line immediately after the closing `---`.
- Do not add it to binary files, JSON, or generated output under `build/` or `.gradle/`.
- Do not remove, translate, or shorten an existing opening line.
