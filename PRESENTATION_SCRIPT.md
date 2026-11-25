# AstroNet: 5-Minute Presentation Script (3 Members)

**Total time:** about 5 minutes
**Before you start:** open the project in VS Code on the demo laptop(s). Do **not** start anything yet.
Words in *(italics)* are actions, not things to say.

| Part | Speaker | Time | Topic |
|---|---|---|---|
| 1 | Member 1 | 0:00 – 1:30 | The problem and our idea |
| 2 | Member 2 | 1:30 – 3:30 | Live demo |
| 3 | Member 3 | 3:30 – 5:00 | What happens when things go wrong + summary |

---

## Part 1: Member 1 (about 1 minute 30 seconds)

### The problem

> "Good morning. We are [names], and our project is called **AstroNet**.
>
> Sometimes a star or an object in the sky suddenly becomes much brighter. This can last only
> a few minutes. If one observatory sees it, it is not enough. Maybe it was a mistake, maybe a
> plane or a cloud. So **other observatories around the world must look at the same spot quickly**
> and say: 'Yes, we see it too' or 'No, we don't.'
>
> Real astronomers have systems like this. We built a small, working version of that idea."

### Our idea

> "Our system has two parts.
>
> The first part is the **server**. Think of it as the **control centre**. It runs on one laptop.
>
> The second part is the **stations**. Each station is like **one observatory**. We have up to five:
> Sri Lanka, India, Australia, Japan and South Africa. Each one runs on a different laptop.
>
> All the laptops talk to each other over a normal Wi-Fi network."

### How it works, in four steps

> "Here is what happens:
>
> **One:** a station sees something bright and sends an **alert** to the control centre,
> together with a small picture of the sky.
>
> **Two:** the control centre tells all the **other** stations: 'Please look at this spot.'
>
> **Three:** each station answers: **seen it**, **not seen**, or **not available**.
>
> **Four:** the control centre counts the answers. If half or more say 'seen it', the result is
> **likely confirmed**. If not, or if nobody answers in 15 seconds, the result is **not sure**.
> Then it sends the final result to everyone.
>
> Now [Member 2] will show you this working live."

---

## Part 2: Member 2 (about 2 minutes) — Live demo

> "First, I start the control centre."

*(Run **Server**. Point at the screen.)*

> "It is now waiting for stations to join. It also shows its address on the network."

*(Start **Station** three times. Type 1, 2 and 3.)*

> "Now I start three stations: Sri Lanka, India and Australia. They **find the control centre
> by themselves**. We don't need to type any address. You can see the server says each one has
> joined."

*(On the server, type `list`.)*

> "This list shows all the stations that are connected right now."

*(Go to the ASTRO-01 window and press **2**.)*

> "Now Sri Lanka has seen something bright, so it sends an alert.
>
> Look at the server: it received the **alert**, gave it a number, EVT-0001, and asked the other
> stations to check. India and Australia looked and sent back their answers.
>
> And here is the **final result** box. It appears on **every** station at the same time,
> so every observatory knows the decision."

*(Open the `received_images` folder.)*

> "The sky picture that Sri Lanka sent is also saved here on the control centre."

*(On ASTRO-02 press **1**.)*

> "Stations can also watch the sky automatically. When I turn this on, India checks the sky every
> few seconds by itself and sends an alert if it finds something. *(wait a moment, then press 1 again)*
> I'll turn it off now.
>
> Now [Member 3] will show what happens when something goes wrong."

---

## Part 3: Member 3 (about 1 minute 30 seconds)

### When a station crashes

> "In real life, computers crash and internet connections drop. Our system handles this."

*(Close the ASTRO-03 terminal with the trash-can icon.)*

> "I just closed Australia suddenly, like a crash. The server notices **immediately** and shows
> 'disconnected'.
>
> Every station also sends a small **'I am alive'** message every 5 seconds. Australia stopped
> sending it, so after about 15 seconds the server marks it **offline**."

### When someone uses the same name twice

*(Start another **Station** and type **2**.)*

> "Now I try to join again as India, but India is already connected. The server **refuses** it.
> So two observatories can never pretend to be the same one."

### Many things at the same time

*(On the server, type `threads`.)*

> "The control centre has to do many jobs **at the same time**: talk to each station, listen for
> the 'I am alive' messages, and count down the 15 seconds for each alert.
>
> So the program is split into **many small workers**, and each worker does one job. Here you can
> see them: one worker for each station, one for the 'I am alive' messages, and one timer per alert.
> Because of this, one slow station never makes the others wait."

### Summary

> "To summarise, AstroNet:
>
> - lets observatories on different computers **share an alert in seconds**,
> - **asks the others to double-check** before deciding,
> - sends the **same final result to everyone**,
> - and keeps working when a station **crashes**, **leaves**, or **does not answer**.
>
> Thank you. We are happy to answer your questions."

---

## Short answers for likely questions

Any member can answer. Keep it short and simple.

| Question | Simple answer |
|---|---|
| Why two kinds of messages (TCP and UDP)? | Important messages (alerts, answers, results) use **TCP**, which makes sure nothing is lost. Small, frequent messages ("I am alive", finding the server) use **UDP**, which is faster and lighter. Losing one of those is not a problem. |
| How do stations find the server? | A station shouts one message to the whole network: "Where is the server?" The server answers with its address. |
| Why is the picture sent separately? | A picture is much bigger than a text message. Sending it on its own connection means alerts are never slowed down. |
| What if two stations join with the same name at the same moment? | The server checks and adds names **one at a time**, so only the first one gets in. |
| Why 50% and 15 seconds? | They are simple rules for the demo. They can be changed in one settings file (`Config.java`). |
| Is the sky data real? | No. Each station **simulates** the sky with random values, so we can demo it anywhere. |

---

## Tips for the day

- Practise once with a timer. Each part should fit its time.
- Use a **phone hotspot**. University Wi-Fi often blocks stations from finding the server.
- If a station can't find the server, type the IP address the server shows. Keep talking while you do it.
- Make the terminal font large (Ctrl + `+`) so the evaluators can read it.
- Only one person touches the laptop at a time. The others point at the screen.
