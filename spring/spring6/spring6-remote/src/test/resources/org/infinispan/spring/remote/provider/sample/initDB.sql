CREATE TABLE books (
  id     INTEGER GENERATED BY DEFAULT AS IDENTITY,
  isbn   VARCHAR(30) NOT NULL,
  author VARCHAR(30) NOT NULL,
  title  VARCHAR(50) NOT NULL,
  PRIMARY KEY(id)
);
CREATE INDEX books_isbn ON books (isbn);