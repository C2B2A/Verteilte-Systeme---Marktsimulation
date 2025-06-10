# Verteilte-Systeme---Marktsimulation

Quellcode für die Marktsimulation verteilter Systeme. 
- mind. 2 Märkte
- mind. 5 Anbieter
- gleichzeitige Zugriffe und Fehlerabwicklung
- Bestellungen ganz oder gar nicht

# Official Task Description
The aim of this task is for you to gain a better understanding of the implementation of distributed systems
and the implementation of business processes in them. To this end, we are developing a system that
simulates long-running business processes using messages and SAGAs. Additionally, the
understanding of concurrent programming will be fostered.
As part of this task, you are to simulate a distributed system which enables users to place an order for
multiple products with a specific marketplace. Depending on the products the marketplace will then contact
each producer/seller to reserve the product and to ship it to the customer. The marketplace must ensure
atomic transactions: customers receive either all ordered products or none at all.
I.e., the customer will not end up with half of the products. At every point in time each seller only has a (very)
limited number of products available. Hence, it is always possible that an order cannot be satisfied. You can
keep the data model as simple as possible and sellers don't have to use a real database for handling the
inventory. The system must support concurrent access, allowing multiple marketplaces to interact with each
seller simultaneously.
It should be possible to instantiate multiple marketplaces (at least 2) and to have multiple sellers/producers
(at least 5).

# Technical Requirements
• No system should ever block, i.e. both the marketplace and the sellers send and receive messages
asynchronously.
• Use ZeroMQ for communication between the systems. For example, the marketplace should send a
request to the seller's system and that system then responds (asynchronously) with a confirmation or a
rejection.
• The orders should either be available in a suitable format and be read in at runtime (properties files,
XML, JSON, Yaml, text, ...) or should be generated at startup. Permanent persistence is not necessary.
Keep the data model as simple as possible.
• The system must handle simulated failures, network latencies and technical errors requiring transaction
rollbacks. You must be able to simulate these aspects in your system in an adjustable manner.
• In this scenario, the marketplace is assumed to be reliable and always available. Hence, only the seller
systems may fail arbitrarily and also the (simulated) network connection between both systems.
It is possible to get up to 5 Bonuspoints when you containerize (using Docker) your application. I. e. it is
theoretically possible to get all points without using individual containers for each seller/producer and
marketplace. If you containerize your application provide all scripts to build the containers; do not upload the
build containers.

# Requirements of the Simulation
The following parameters should be variable in your program or be adjustable via a configuration file:
• Arrival rate of orders at the marketplace; i.e. the delay between orders.
• Average processing time for an order by a seller.
• Probability with which a seller receives a message ...
• and accepts it but does not process it (simulated crash)
• and processes it (i.e. makes a change to its status) but does not confirm the message
• and successfully processes the message (i.e. possibly changes its status but in any case confirms
the message); we differentiate:
• the order was successful.
• the order could not be carried out because no more products are available, which will
then require a rollback.
(Use normal distributions as the basis for the required random numbers).

# Documentation
Explicitly document all technical and business related problems that may occur. For all these cases,
explicitly document what the compensating transactions look like in these cases and who takes over the
coordination and when. It must be clearly recognizable from the documentation that the systems are
eventually consistent. As said: in this scenario, the marketplace can be assumed to be reliable and error-free.

# General Requirements
• The task should be solved in groups of four. If necessary, individual marks will be awarded.
• The implementation should be done in Java.
• Except for logging, tracing, visualization and network communication (ZeroMQ), no external libraries
shall be used.
• Ensure a clean, comprehensible design!
(The logic for data storage (if required at all) can be implemented alongside the business logic as part of
this task).
• Focus on concurrency, possible sources of errors and the correctness of the transactions; with regard to
the modeling of a seller's inventory, take the liberty of keeping the model as simple as possible. Of
course, products must be limited and it must be possible for orders to fail because no more product is
available. Make sure that your test data/test data generator will trigger business related issues/errors at
least occasionally!

# Submission
• each group must produce a document describing the architecture and test concept (approx. 3 - 5 pages)
• each group must submit a document describing all error cases and how the system reacts (as described
above)
• Java source code and test data sets with appropriate comments
• executable class files with corresponding initialization files and a short user manual must be submitted
(as JAR file(s) that can be started directly ‘java -jar <jar file> <parameter>’)
• a video with a voice over showing a demo of the solution and a discussion of the implementation.
The video should:
a. demonstrate the functionality and error recovery behavior. For this purpose, the rate of order
generations can be reduced so that effectively only very few orders (with corresponding errors)
are observed. A list of all error cases that have been tested. A clear distinction must be made
between the technical error cases and the business related error cases.
b. demonstrate system behavior under high concurrent load (dozens of simultaneous requests) using
logs and visualizations.
Large video files should be avoided if possible, but may be stored in a cloud. The link where the video
can be downloaded must then be specified in the documentation and must be available for at least 12
weeks after the submission deadline; make sure you have sufficient access rights.
Remember: Brevity is the spice of life!; cut/speed up your video if there are sections that are not
relevant.
• Submit a document detailing each group member's specific contributions and responsibilities.
(E.g. ´´Lara implemented the marketplace´´; ´´Tim set up the test concept and the test environment´´;
´´Tom implemented the seller service´´; ´´All identified potential error scenarios.´´.)
• A declaration on honor personally signed by all group members with the following wording must also be
submitted:
Hiermit erklären wir ehrenwörtlich, dass wir die vorliegende Portfolio-Arbeit zur Vorlesung
”Distributed Systems” bestehend aus Ausarbeitung, Dokumentation, Programmcode und Video
selbstständig verfasst und keine anderen als die angegebenen Quellen und Hilfsmittel benutzt
haben.
