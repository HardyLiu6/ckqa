# GraphRAG History PoC

- live: `true`
- caseCount: `3`
- graphragVersion: `3.0.9`
- dataDirUri: `user_12/kb_5/build_20/index/output`

## Cases

| case | local history | local ms | local sources | local recall@3 | CKQA hybrid | hybrid ms | hybrid sources | hybrid recall@3 |
| --- | --- | ---: | ---: | ---: | --- | ---: | ---: | ---: |
| `Q2001` 它和操作系统有什么关系？ | success | 42220 | 2 | 0.3333 | success | 54687 | 10 | 0.3333 |
| `Q2002` 它和操作系统有什么关系？ | success | 28986 | 3 | 0.0 | success | 37523 | 8 | 0.0 |
| `Q2003` 它和用户态有什么关系？ | success | 42811 | 1 | 0.0 | success | 71680 | 7 | 0.0 |
