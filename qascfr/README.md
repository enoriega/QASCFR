# Workflow

- 1. Convert the Jsonl files to tsv with `extract_questions_from_json.py`. Output: `.tsv` file with question and answer for each set
- 2a. Annotate the corpus using processors with `org.clulab.exec.AnnotateQASCCorpus`. Output: Directory with `.ser` files
- 2b. Annotate the questions from the different sets using `org.clulab.exec.AnnotateQASCQuestions`. Output: `.ser` for the dataset
- 3a. Extract  the relations using `RelationExtractor`. Output `.ser` file with the relations
- 3b. Extract noun phrases with `NounPhraseExtractor`. Output `.ser` file with noun phrases per set file
- 4. Generate the graph files with `BuildGroundTruthGraph`. Output multiple `.tsv` files for building a graph with Neo4J.
- 5. Generate end points for path search with `EmbedQuestion`. Output `.csv` file with endpoints for each question