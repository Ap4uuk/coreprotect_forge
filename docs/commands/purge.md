# `/co purge`

Purge old block data to free disk space when you no longer need historical records.

## Usage

```
/co purge t:<time> r:<world> i:<include>
```

- `t:<time>` — age threshold to delete. Example: `/co purge t:30d` removes data older than 30 days.
- `r:<world>` — optional world selector (CoreProtect v19+). Example: `/co purge t:30d r:#world_nether` purges only the Nether.
- `i:<include>` — optional comma-separated block types (CoreProtect v23+). Example: `/co purge t:30d i:stone,dirt` purges only stone and dirt entries.

## Restrictions

- In-game execution can only purge data older than **30 days**.
- Console execution can purge data older than **24 hours** or more.

## MySQL optimization

In CoreProtect v2.15+, add `#optimize` to also optimize MySQL tables and reclaim disk space, e.g. `/co purge t:30d #optimize`.

> **Note:** `#optimize` significantly slows the purge and is unnecessary for SQLite, which optimizes automatically.
