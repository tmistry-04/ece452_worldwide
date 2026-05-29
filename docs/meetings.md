# Team Worldwide: Meeting Notes

<details>
<summary>May 26</summary>

**Attendees:** All members

#### Project Status

- **Final app idea:** Recipe / fridge inventory app
- **P1** submitted this morning
- **P3** in progress → [Project Proposal](https://docs.google.com/document/d/1YEP49TQ5AGa8CXvQw4jCfg91kmvioL5fZ9LoEEO1c5Y/edit?tab=t.0)

#### Core Scope

**Main focus:** Fridge inventory management. Users log what ingredients they have, and the app suggests recipes accordingly. Recipe steps are not in scope for the core version.

##### Functional Requirements
- Fridge inventory tracking (log ingredients + expiration dates)
- Recipe suggestions based on available ingredients
- Scan receipt to log groceries (OCR, e.g. Amazon Textract)
- Push notifications (expiring ingredients, low stock, spoilage indicators)
- Food safety notes
- User accounts to store ingredients and preferences
  - Account-based (requires server) vs. on-device storage
  - Authentication option: **Amazon Cognito**
  - Security risks to be clarified

##### AI Features
- Photo → "Is this cooked correctly?"
- Ingredient substitution suggestions
- AI is for smart/optional features, not required for every recipe step
- **Open question:** Server needed to store API keys securely? → *Ask Lesley*

##### Recipe Display (open questions)
- Pull recipes externally or maintain an internal recipe bank?
- Present as web view, slides, or step-by-step interactive format?
- Multiple timers for different cooking steps

#### Stretch Goals

| Feature | Notes |
|---|---|
| Social features | User accounts, sharing recipes |
| Grocery scanning | Scan items as purchased, auto-log + expiration dates |
| Meal wishlist → grocery list | Generate shopping list from planned meals |
| Text recipe → app recipe converter | Seamlessly import external recipes |

##### User Journey (hierarchy of needs)
1. *"Here's what's in my fridge. Help me use it before it goes bad."*
2. *"Here's what I have. What can I make?"*
3. *"Show me the recipe step-by-step in digestible, interactive chunks with timers."*

#### Non-Functional Requirements

- **Usability:** manual ingredient entry is acceptable but not ideal; scanning (receipt/barcode) improves this significantly
- **Convenience**

#### Target Users

- People learning to cook
- People who want to manage their fridge / reduce food waste
- People who just want to figure out what to eat

#### Team Roles

| Member | Role |
|---|---|
| Adora | Frontend / Design Lead (?) |
| Yuna | Frontend / Team Lead |
| Tanisha | TBD |
| Dhyey | Backend (AI, no databases) |
| Asel | TBD |
| Tony | Technical Lead |

> **Note:** Most members are less experienced with UI; lean toward simpler frontend design.

#### Action Items

- [ ] Discuss rough project timeline (next meeting)
- [ ] Weekly update due **June 1** *(async)*
- [ ] Everyone: get **Android Studio** installed and working
- [ ] Rough draft of P3 doc ready by next meeting
- [ ] Ask Lesley: do we need a server to securely store API keys?

**Next meeting:** June 2 @ 11:00 AM, E7

</details>
<details>
<summary>May 21</summary>

**Attendees:** All members

- Brief introductions
- Decided on Yuna being team lead
- Other members will have loosely defined roles, might change this once we understand our project workload better
- Future meetings on Tuesday @ 11AM (except May 26th, which will be at 6PM)
- Emailed P0 deliverable
- Created Team Contract, README and GitHub repo
- Shared preliminary brainstormed ideas: band/instrument helper app and interactive recipe app/cooking helper

</details>