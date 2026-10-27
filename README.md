# Exam Result System

A beginner Java Swing app that stores exam marks in MySQL using JDBC and shows pass/fail reports.

## Features
- Save student details and marks for Maths, Science and English
- A student passes only if every subject is 40 or above
- View one student's result by roll number
- List passed and failed students separately

## Run
1. Install JDK 11+, MySQL, and the MySQL Connector/J jar.
2. Set `USER` and `PASSWORD` in `ExamResultSwing.java`.
3. Compile: `javac ExamResultSwing.java`
4. Run: `java -cp ".:path/to/mysql-connector-j.jar" ExamResultSwing`
