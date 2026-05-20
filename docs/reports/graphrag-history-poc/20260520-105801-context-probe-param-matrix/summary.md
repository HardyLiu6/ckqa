# GraphRAG History Context Probe

- dataDirUri: `user_12/kb_5/build_20/index/output`
- externalCalls: `false`
- method: local parquet + gold_entities anchor + no answer generation

| variant | cases | failed | token warnings | avg context chars-as-tokens | avg ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| `current-5k-6x6-turn5` | 3 | 0 | 0 | 3300.67 | 3.67 |
| `candidate-12k-4x4-turn3` | 3 | 0 | 0 | 5594.33 | 3.0 |
| `candidate-24k-4x4-turn3` | 3 | 0 | 0 | 10372.67 | 3.0 |
