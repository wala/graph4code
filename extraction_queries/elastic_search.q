query = {
        'from': 0, 'size': 5000,
        "query": {
            "multi_match": {
                "query": ' '.join(should_clauses),
                "type": "most_fields",
                "fields": ["content"],
                "operator": "AND"
            }
        }
    }
