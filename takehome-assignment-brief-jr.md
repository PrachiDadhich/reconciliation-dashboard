# Take-Home Assignment: Associate Software Developer

Thank you for taking the time to do this. The purpose of this task is to see how you build something complete, end to end, on your own, and how you think about data that does not line up.

Please read this document carefully and in full before you start.

---

## Time

- You have **24 hours** from the moment you receive this task.
- The task is deliberately scoped so that a focused developer can complete it well inside that window. Please do not over-build. A smaller product that is correct, complete, and live is far better than a large one that is half-working.

---

## The Scenario

An online store has two systems that should agree with each other, and do not.

- **`orders.csv`** is exported from the store's order system. It is what the store believes it sold.
- **`payments.csv`** is exported from its payment processor. It is what actually got charged, refunded, or settled.

In theory, every completed order has exactly one matching payment for the right amount. In practice, the two files disagree in a number of ways, and nobody currently knows where the money is leaking.

Both files are attached. They contain real-world messiness. **Part of the task is discovering what is wrong with them, so we are not going to tell you.** Assume nothing about the data until you have looked.

---

## What to Build

A deployed web application that ingests these two datasets, reconciles them, and presents the result as a **dashboard** that someone responsible for the store's revenue could actually use.

### Core requirements

**1. Authentication**
Users can sign up and log in. Users only ever see their own data. Protect your API routes properly, hash passwords or use a reputable auth provider, and handle sessions or tokens sensibly.

**2. Data ingestion**
A logged-in user can load the two datasets into the system, through file upload or an import step of your design. The data is stored in a real database. You may use any database.

**3. Reconciliation engine (the core of this task)**
Your backend must match orders against payments and identify every case where they do not agree. You decide what counts as a discrepancy, how to classify each type, and where you draw your tolerances. Be prepared to defend those choices.

Your reconciliation should be **deterministic and repeatable**: the same input must always give the same result. Do not use an LLM to do the matching itself.

**4. Dashboard**
A clear dashboard showing at least:
- Headline figures: total orders, total payments, total value reconciled, total value in dispute, and how much money is at risk.
- A breakdown of discrepancies by type, with at least one meaningful chart.
- A drill-down table of the individual discrepancies, filterable and searchable, so a user can go from a headline number to the specific rows behind it.

The dashboard should answer, at a glance: *how bad is it, what kind of problems do we have, and which ones should we look at first?*

**5. LLM integration**
Use OpenAI or another LLM API to add a layer of explanation on top of your deterministic results. At minimum, the system should be able to produce a plain-language explanation of a given discrepancy or set of discrepancies: what likely happened, and what someone should do about it.

Requirements for this part:
- The LLM must be called from the **backend**. Never expose API keys to the frontend, and never commit them.
- Ask the model for **structured output** where appropriate, and handle the case where it returns something malformed or unexpected.
- Make a deliberate choice about **temperature** and any other parameters you consider relevant, and explain that choice in your README.
- The LLM explains and summarises. It must not be the thing that decides whether two records match.

**6. Frontend quality**
Handle loading and error states clearly, including while the LLM call is in flight and when it fails. Clarity and usability matter more than visual polish.

---

## Constraints

- **Any stack.** Use whichever frontend framework, backend language, database, and hosting you are most effective with. We have no preference.
- **Deploy everything.** Frontend, backend, and database must all be live and hosted. It must work from a fresh browser on our machines, not just on yours. You may deploy anywhere.
- **GitHub.** The code must be in a GitHub repository we can access, either public or private with an invite.
- **Do not mention any company.** Our company name and product must not appear anywhere in the code, commits, README, repository name, or the application itself. Keep it entirely generic.
- **AI coding tools are allowed and encouraged.** Claude Code, Cursor, Copilot, or anything similar. We use these ourselves. The one condition is that you understand everything you ship.

---

## What to Submit

Reply with:

1. The **GitHub repository** URL.
2. The **live application URL**, plus the backend URL if it is separate.
3. **Test credentials** we can log in with, or working sign-up.
4. A **README** in the repository containing:
   - How to set up and run it locally.
   - An overview of the architecture and how the pieces fit together.
   - **Your reconciliation logic**: the discrepancy types you identified, how you matched records, the tolerances or rules you chose, and why.
   - **What you found in the data**: what is actually wrong with these two files, and what it would mean for the business.
   - Your LLM approach: prompting, temperature and why, and how you handle bad responses.
   - What you would improve or build next with more time.
   - A brief note on how you used AI tools, if you did.
5. An **`.env.example`** listing required environment variables, with no real secrets.

---

## How We Will Evaluate It

- **Correctness of the reconciliation.** Did you find the real problems in the data, and did you avoid inventing false ones?
- **Quality of thinking.** How you classified discrepancies, where you set tolerances, and how well you justify those decisions.
- **End-to-end completeness.** Does the full flow work on the live deployment, from sign-up to ingestion to dashboard to drill-down?
- **Product judgment.** Is the dashboard something a real person could act on, or just charts on a page?
- **Robustness.** Does auth actually protect the right things? Does the system behave when the data or the LLM misbehaves?
- **Code clarity and Git hygiene.** Readable, organised code and meaningful commits rather than one large dump.
- **README quality.** Can we understand your decisions and your findings from it?

---

## The Next Round

If you progress, the next round is a review of exactly this project. We will walk through the code together and ask how and why you built it the way you did, including specific commits and specific decisions, and we will ask you to defend your reconciliation rules. Please be ready to explain any part of what you submit.

Using AI tools is completely fine. Shipping something you cannot explain is not.

Good luck. We are looking forward to seeing what you build.
