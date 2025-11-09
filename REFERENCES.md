---

## 📚 References & Acknowledgements

This project, **QueueCTL**, was developed independently by **Nalan Krishna V** as part of a backend developer internship assignment.  
The following resources, frameworks, and articles were referenced to ensure accurate implementation and understanding of concepts like job queues, exponential backoff, concurrency, and dead-letter queue design.

### 🧠 Conceptual References

- Kir Shatrov — *Mastering SKIP LOCKED in MySQL*  
  [https://kirshatrov.com/posts/fast-skip-locked](https://kirshatrov.com/posts/fast-skip-locked)

- Percona — *Using SKIP LOCK for Queue Processing in MySQL*  
  [https://www.percona.com/blog/using-skip-lock-for-queue-processing-in-mysql/](https://www.percona.com/blog/using-skip-lock-for-queue-processing-in-mysql/)

- AWS Builders Library — *Timeouts, Retries, and Backoff with Jitter*  
  [https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/](https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/)

- Better Stack — *Mastering Exponential Backoff in Distributed Systems*  
  [https://betterstack.com/community/guides/monitoring/exponential-backoff/](https://betterstack.com/community/guides/monitoring/exponential-backoff/)

- Rashad Ansari — *Strategies for Successful Dead Letter Queue Event Handling*  
  [https://rashadansari.medium.com/strategies-for-successful-dead-letter-queue-event-handling-e354f7dfbb3e](https://rashadansari.medium.com/strategies-for-successful-dead-letter-queue-event-handling-e354f7dfbb3e)

- Uber Engineering Blog — *Building Reliable Reprocessing and Dead Letter Queues with Kafka*  
  [https://www.uber.com/blog/reliable-reprocessing/](https://www.uber.com/blog/reliable-reprocessing/)

- AWS Documentation — *Using Dead Letter Queues to Process Undelivered Events*  
  [https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-rule-dlq.html](https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-rule-dlq.html)

---

### ⚙️ Technical & Library References

- **Picocli** — Command-line interface framework for Java  
  [https://picocli.info/](https://picocli.info/)

- **HikariCP** — High-performance JDBC connection pool  
  [https://github.com/brettwooldridge/HikariCP](https://github.com/brettwooldridge/HikariCP)

- **Jackson Databind** — JSON serialization/deserialization library  
  [https://github.com/FasterXML/jackson](https://github.com/FasterXML/jackson)

- **SLF4J** — Simple Logging Facade for Java  
  [https://www.slf4j.org/](https://www.slf4j.org/)

- **MySQL JDBC Driver (Connector/J)** — Database connectivity for Java  
  [https://dev.mysql.com/doc/connector-j/8.0/en/](https://dev.mysql.com/doc/connector-j/8.0/en/)

- **Maven Shade Plugin** — Bundling dependencies into a single executable JAR  
  [https://maven.apache.org/plugins/maven-shade-plugin/](https://maven.apache.org/plugins/maven-shade-plugin/)

---

### ☁️ Tools & Infrastructure

- **Aiven Cloud MySQL** — Managed MySQL instance used for deployment and remote DB testing  
  [https://aiven.io/mysql](https://aiven.io/mysql)

- **GitHub** — Source control and collaboration platform  
  [https://github.com](https://github.com)

- **Eclipse IDE** — Development and debugging environment  
  [https://www.eclipse.org/ide/](https://www.eclipse.org/ide/)

- **Vercel** — Cloud deployment platform (explored for future web-based monitoring)  
  [https://vercel.com](https://vercel.com)

---

### 🙏 Acknowledgements

- The **Java open-source community** for their documentation and examples.  
- **Stack Overflow** discussions that helped resolve minor JDBC and SLF4J compatibility issues.  
- **Mentors and peers** who provided valuable suggestions during debugging and architecture design.

All architecture design, worker logic, exponential backoff implementation, CLI structure, and database operations were independently implemented by **Nalan Krishna V** for this project.  
This repository is fully original, and external references above were used only for conceptual understanding.

---

> © 2025 Nalan Krishna V  
> Licensed under the MIT License.  
> This project is for educational and evaluation purposes only.
