"""Writes benchmarks/fixtures/outline-pypdf.pdf: an outline written by pypdf.

    python3 benchmarks/outline-fixtures/pypdf-outline.py   # needs `pip install pypdf`
"""
from pathlib import Path

import pypdf

fixtures = Path(__file__).resolve().parent.parent / "fixtures"
reader = pypdf.PdfReader(fixtures / "outline.pdf")
writer = pypdf.PdfWriter()
for page in reader.pages[:10]:
    writer.add_page(page)
chapter = writer.add_outline_item("Kapitel 1", 0)
writer.add_outline_item("Abschnitt 1.1", 1, parent=chapter)
section = writer.add_outline_item("Abschnitt 1.2", 2, parent=chapter)
writer.add_outline_item("Tief — deep", 3, parent=section)
writer.add_outline_item("日本語の章", 5)
writer.add_outline_item("Ende", 9)
writer.write(fixtures / "outline-pypdf.pdf")
