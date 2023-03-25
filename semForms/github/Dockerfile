FROM continuumio/miniconda3

WORKDIR /app

# Make RUN commands use `bash --login`:
SHELL ["/bin/bash", "--login", "-c"]

COPY environment.yml .

RUN conda env create -f environment.yml

SHELL ["conda", "run", "-n", "github", "/bin/bash", "-c"]


# Demonstrate the environment is activated:
RUN echo "Make sure flask is installed:"

RUN python -c "import flask"

COPY search_github_code.py .

CMD ["conda", "run", "--no-capture-output", "-n", "github", "python", "search_github_code.py"]