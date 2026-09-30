# Comment guidelines

Comments explain what the code cannot say by itself. Good names and small methods come first;
a comment is the last resort, not a substitute for them.

## General rules

1. **English only.** Full sentences: capital letter at the start, period at the end.
2. **Explain why, not what.** Do not restate the code, the method name or the types.
   - Bad: `// Saves the page`
   - Good: `// Previous chunks are deleted first so a page never mixes two versions.`
3. **Keep comments true.** A wrong comment is worse than none: update or delete it together with
   the code it describes.
4. **No commented-out code.** Version control keeps the history.
5. **No anonymous TODOs.** If unavoidable, use `TODO(<owner>): <what and why>`.
6. **Use Javadoc tags for references**: `{@code value}` for literals and code, `{@link Type}` for
   types and methods. No arrows (`→`) or shorthand; write the relationship in words.

## Javadoc: where it is required

| Element | Required | Content |
|---|---|---|
| Public class, interface or enum (controllers, services, clients, components, mappers, configuration, exceptions, constants) | Yes | One sentence with its responsibility. |
| Public method of a service, client or component | Yes | What it does and any non-obvious behavior. `@param` / `@return` only when they add information beyond the name. `@throws` for domain exceptions the caller must handle. |
| Record, DTO or JPA entity whose fields are self-explanatory | No | Add one sentence only if its purpose or a field is not obvious. |
| Mapper method fully described by its `@Mapping` annotations | No | Document only parameters with a non-obvious meaning. |
| Spring Data repository method with a derived query | No | |
| Private method | No | Only when the reasoning is not obvious from the code. |
| Overridden method | No | Unless the behavior differs from the contract. |
| Application class (`main`) | No | |
| Test method | No | The method name describes the expected behavior. |

Format:

```java
/** Synchronizes a Confluence space, indexing only new pages or pages with a new version. */
```

- Start with a verb in third person for methods (`Returns`, `Indexes`) and with a noun phrase for
  types (`Client for the Confluence Cloud REST API v2.`).
- Use a single line when it fits in the line length; otherwise a block with one sentence per idea.

## Inline comments (`//`)

Only for decisions that a reader would question or could break by "simplifying" the code:
workarounds, external constraints, performance or ordering reasons. Place them on the line above
the code they explain.

## SQL changesets and properties files

- **Changesets:** one comment block after `--changeset` explaining the purpose of the object and any
  non-obvious decision (indexes, types, missing constraints). Do not describe each column.
- **Properties:** one comment per group of related properties, only when the reason for the values
  is not obvious (for example, why a feature is disabled).
