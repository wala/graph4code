SELECT 
 max(concat(f.repo_name, ' ', f.path)) as repo_path,
 c.content
FROM `bigquery-public-data.github_repos.files` as f
JOIN `bigquery-public-data.github_repos.contents` as c on f.id = c.id
JOIN (
      --this part of the query makes sure repo is watched at least twice since 2017
      SELECT repo FROM(
        SELECT 
          repo.name as repo
        FROM `githubarchive.year.2017` WHERE type="WatchEvent"
        UNION ALL
        SELECT 
          repo.name as repo
        FROM `githubarchive.month.2019*` WHERE type="WatchEvent"
        )
      GROUP BY 1
      HAVING COUNT(*) >= 2
      ) as r on f.repo_name = r.repo
WHERE 
  (f.path like '%.py' or f.path like '*.ipynb') and --with python extension
  c.size < 15000 and --get rid of ridiculously long files
  REGEXP_CONTAINS(c.content, r'def ') --contains function definition
group by c.content