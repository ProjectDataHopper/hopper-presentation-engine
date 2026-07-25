# Recipe: multi-line notes

Use `HTextBlockComponent` (not label):

- `text` with `\n` hard breaks
- `wrap: true` for soft wrap
- `maxWidth` if no left+right stretch
- `paginate: true` for long report notes across pages

Template: [templates/presentation-text-block.json](../templates/presentation-text-block.json).
