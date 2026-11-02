"use strict";

const STORAGE_KEY = "QServe_data";
const ACCOUNTS_KEY = "QServe_accounts";
const SESSION_KEY = "QServe_session";

// FIREBASE INITIALIZATION
const firebaseConfig = {
    apiKey: "AIzaSyCG3CIquWNYHQQaLLZA0Se24Poaz3dfdfY",
    authDomain: "dentalclinicappointmentapp.firebaseapp.com",
    projectId: "dentalclinicappointmentapp",
    storageBucket: "dentalclinicappointmentapp.firebasestorage.app",
    messagingSenderId: "280620205827",
    appId: "1:280620205827:web:dentalclinicappointmentapp"
};

let auth = null;
let db = null;
let isFirebaseAvailable = false;

try {
    if (typeof firebase !== "undefined") {
        if (!firebase.apps.length) {
            firebase.initializeApp(firebaseConfig);
        }
        auth = firebase.auth();
        db = firebase.firestore();
        isFirebaseAvailable = true;
    }
} catch (e) {
    console.warn("Firebase initialization failed, falling back to local state:", e);
}

const ADMIN_ACCOUNT = {
    name: "System Administrator",
    email: "admin@qserve.com",
    password: "Admin123",
    role: "admin"
};

const defaultAppointments = [
    {
        id: "1",
        userId: "",
        patientName: "Alex Johnson",
        name: "Alex Johnson",
        email: "",
        service: "Check-up",
        dentist: "John Benedict Centeno",
        date: "2026-11-02",
        time: "09:00",
        status: "Approved",
        queueNumber: 1,
        queue: "Q-001"
    },
    {
        id: "2",
        userId: "",
        patientName: "Maria Santos",
        name: "Maria Santos",
        email: "",
        service: "Cleaning",
        dentist: "Clemence Valdez",
        date: "2026-11-02",
        time: "10:00",
        status: "Pending",
        queueNumber: 0,
        queue: null
    },
    {
        id: "3",
        userId: "",
        patientName: "John Reyes",
        name: "John Reyes",
        email: "",
        service: "Filling",
        dentist: "John Benedict Centeno",
        date: "2026-11-02",
        time: "11:00",
        status: "Approved",
        queueNumber: 2,
        queue: "Q-002"
    }
];

const defaultServices = [
    {
        name: "Check-up",
        description: "General dental check-up and consultation."
    },
    {
        name: "Cleaning",
        description: "Professional dental cleaning and scaling."
    },
    {
        name: "Filling",
        description: "Tooth restoration and dental filling service."
    },
    {
        name: "Braces",
        description: "Orthodontic consultation and adjustment."
    },
    {
        name: "General Consultation",
        description: "General appointment and consultation service."
    }
];

const defaultDentists = [
    {
        id: 1,
        name: "John Benedict Centeno",
        specialty: "General Dentistry"
    },
    {
        id: 2,
        name: "Clemence Valdez",
        specialty: "Dental Surgery"
    },
    {
        id: 3,
        name: "Praswel Jim Dimaano",
        specialty: "Orthodontics"
    }
];

let appointments = [];
let services = [];
let dentists = [];
let notifications = [];
let queue = [];

let currentQueue = "Q-000";
let nextQueueNumber = 3;
let currentUser = null;

// STATUS HELPER

function normalizeStatus(st) {
    if (!st) return "Pending";
    const s = String(st).trim().toLowerCase();
    if (s === "pending") return "Pending";
    if (s === "approved" || s === "confirmed") return "Approved";
    if (s === "serving") return "Serving";
    if (s === "completed" || s === "done") return "Completed";
    if (s === "cancelled") return "Cancelled";
    if (s === "rejected") return "Rejected";
    return st.charAt(0).toUpperCase() + st.slice(1);
}

// STORAGE & FIREBASE SYNC

function saveData() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
        appointments,
        services,
        dentists,
        notifications,
        queue,
        currentQueue,
        nextQueueNumber
    }));
}

function rebuildQueueFromAppointments() {
    const activeQueue = [];
    let currentServing = "Q-000";

    const sorted = [...appointments].sort((a, b) => {
        if (a.queueNumber && b.queueNumber) return a.queueNumber - b.queueNumber;
        return (a.date + a.time).localeCompare(b.date + b.time);
    });

    sorted.forEach(a => {
        const normSt = (a.status || "").toLowerCase();
        if ((normSt === "approved" || normSt === "serving") && a.queueNumber > 0) {
            const qStr = `Q-${String(a.queueNumber).padStart(3, '0')}`;
            if (normSt === "serving") {
                currentServing = qStr;
            }
            activeQueue.push({
                number: qStr,
                queueNumber: a.queueNumber,
                name: a.patientName || a.name || "Patient",
                email: a.email || "",
                status: normSt === "serving" ? "Serving" : "Waiting",
                appointmentId: a.id
            });
        }
    });

    queue = activeQueue;
    currentQueue = currentServing;
}

function initFirestoreSync() {
    if (!isFirebaseAvailable || !db) return;

    db.collection("appointments").onSnapshot(snapshot => {
        const remoteAppointments = [];
        let maxQ = 0;

        snapshot.forEach(doc => {
            const data = doc.data();
            const qNum = Number(data.queueNumber) || 0;
            if (qNum > maxQ) maxQ = qNum;

            const nameVal = data.patientName || data.name || "Patient";
            const normStatus = normalizeStatus(data.status);

            remoteAppointments.push({
                id: doc.id,
                userId: data.userId || "",
                patientName: nameVal,
                name: nameVal,
                email: data.email || "",
                service: data.service || "",
                dentist: data.dentist || "",
                date: data.date || "",
                time: data.time || "",
                status: normStatus,
                queueNumber: qNum,
                queue: qNum > 0 ? `Q-${String(qNum).padStart(3, '0')}` : null
            });
        });

        appointments = remoteAppointments;
        if (maxQ >= nextQueueNumber) {
            nextQueueNumber = maxQ + 1;
        }

        rebuildQueueFromAppointments();
        saveData();
        renderAll();
    }, err => {
        console.error("Firestore sync error:", err);
    });
}

function loadData() {
    try {
        const saved = JSON.parse(localStorage.getItem(STORAGE_KEY));

        if (!saved) {
            appointments = defaultAppointments.map(a => ({ ...a }));
            services = defaultServices.map(s => ({ ...s }));
            dentists = defaultDentists.map(d => ({ ...d }));

            notifications = [
                {
                    message: "QServe system initialized.",
                    time: "Just now"
                }
            ];

            currentQueue = "Q-000";
            nextQueueNumber = 3;
            rebuildQueueFromAppointments();
        } else {
            appointments = Array.isArray(saved.appointments) ? saved.appointments : [];

            services = Array.isArray(saved.services) && saved.services.length
                ? saved.services
                : defaultServices.map(s => ({ ...s }));

            const savedDentists = Array.isArray(saved.dentists) ? saved.dentists : [];

            dentists = [
                ...defaultDentists.map(d => ({ ...d })),
                ...savedDentists.filter(existing =>
                    !defaultDentists.some(d => d.id === existing.id)
                )
            ];

            notifications = Array.isArray(saved.notifications) ? saved.notifications : [];
            currentQueue = saved.currentQueue || "Q-000";
            nextQueueNumber = Number(saved.nextQueueNumber) || 3;

            appointments.forEach(a => {
                if (!a.patientName) a.patientName = a.name || "Patient";
                if (!a.name) a.name = a.patientName;
                if (a.dentist === undefined) a.dentist = "";
                a.status = normalizeStatus(a.status);
            });

            rebuildQueueFromAppointments();
        }

        saveData();
    } catch (error) {
        console.error("Could not load data:", error);

        appointments = defaultAppointments.map(a => ({ ...a }));
        services = defaultServices.map(s => ({ ...s }));
        dentists = defaultDentists.map(d => ({ ...d }));
        notifications = [];
        currentQueue = "Q-000";
        nextQueueNumber = 3;
        rebuildQueueFromAppointments();

        saveData();
    }
}

function getAccounts() {
    try {
        const accounts = JSON.parse(localStorage.getItem(ACCOUNTS_KEY));
        return Array.isArray(accounts) ? accounts : [];
    } catch {
        return [];
    }
}

function saveAccounts(accounts) {
    localStorage.setItem(ACCOUNTS_KEY, JSON.stringify(accounts));
}

function getSession() {
    try {
        return JSON.parse(sessionStorage.getItem(SESSION_KEY));
    } catch {
        return null;
    }
}

function saveSession(user) {
    sessionStorage.setItem(SESSION_KEY, JSON.stringify({
        uid: user.uid || "",
        name: user.name,
        email: user.email,
        role: user.role
    }));
}

function clearSession() {
    sessionStorage.removeItem(SESSION_KEY);
}

// AUTHENTICATION

function showAuthView(view) {
    document.getElementById("loginView").hidden = view !== "login";
    document.getElementById("signupView").hidden = view !== "signup";

    document.getElementById("loginError").textContent = "";
    document.getElementById("signupError").textContent = "";
}

function loginUser(user) {
    currentUser = {
        uid: user.uid || "",
        name: user.name,
        email: user.email,
        role: user.role
    };

    saveSession(currentUser);

    document.getElementById("authSection").hidden = true;

    if (user.role === "admin") {
        openAdminPortal();
    } else {
        openCustomerPortal();
    }
}

document.getElementById("loginForm").addEventListener("submit", async function(e) {
    e.preventDefault();

    const email = document.getElementById("loginEmail").value.trim().toLowerCase();
    const password = document.getElementById("loginPassword").value;
    const error = document.getElementById("loginError");

    error.textContent = "";

    if (email === ADMIN_ACCOUNT.email.toLowerCase() && password === ADMIN_ACCOUNT.password) {
        loginUser(ADMIN_ACCOUNT);
        return;
    }

    if (isFirebaseAvailable && auth) {
        try {
            const userCred = await auth.signInWithEmailAndPassword(email, password);
            const fbUser = userCred.user;
            const userObj = {
                uid: fbUser.uid,
                name: fbUser.displayName || email.split("@")[0],
                email: fbUser.email,
                role: "customer"
            };
            loginUser(userObj);
            return;
        } catch (err) {
            console.warn("Firebase Auth login attempt:", err.message);
        }
    }

    const account = getAccounts().find(user =>
        user.email.toLowerCase() === email && user.password === password
    );

    if (!account) {
        error.textContent = "Invalid email address or password.";
        return;
    }

    loginUser(account);
});

document.getElementById("signupForm").addEventListener("submit", async function(e) {
    e.preventDefault();

    const name = document.getElementById("signupName").value.trim();
    const email = document.getElementById("signupEmail").value.trim().toLowerCase();
    const password = document.getElementById("signupPassword").value;
    const confirmPassword = document.getElementById("confirmPassword").value;
    const error = document.getElementById("signupError");

    error.textContent = "";

    if (password.length < 8) {
        error.textContent = "Password must contain at least 8 characters.";
        return;
    }

    if (password !== confirmPassword) {
        error.textContent = "Passwords do not match.";
        return;
    }

    const accounts = getAccounts();

    if (email === ADMIN_ACCOUNT.email.toLowerCase() || accounts.some(user => user.email.toLowerCase() === email)) {
        error.textContent = "An account with this email already exists.";
        return;
    }

    let uid = "";
    if (isFirebaseAvailable && auth) {
        try {
            const userCredential = await auth.createUserWithEmailAndPassword(email, password);
            if (userCredential.user) {
                uid = userCredential.user.uid;
                await userCredential.user.updateProfile({ displayName: name });
            }
        } catch (err) {
            console.warn("Firebase Auth signup notice:", err);
        }
    }

    accounts.push({
        id: Date.now(),
        uid: uid,
        name,
        email,
        password,
        role: "customer"
    });

    saveAccounts(accounts);

    alert("Account created successfully. You can now log in.");
    this.reset();
    document.getElementById("loginEmail").value = email;
    showAuthView("login");
});

function logout() {
    currentUser = null;
    clearSession();
    if (isFirebaseAvailable && auth) {
        auth.signOut().catch(() => {});
    }

    document.getElementById("adminApp").hidden = true;
    document.getElementById("customerApp").hidden = true;
    document.getElementById("authSection").hidden = false;

    document.getElementById("loginForm").reset();
    document.getElementById("signupForm").reset();

    showAuthView("login");
}

// PORTAL INITIALIZATION

function openAdminPortal() {
    document.getElementById("customerApp").hidden = true;
    document.getElementById("adminApp").hidden = false;

    document.getElementById("adminName").textContent = currentUser.name;
    document.querySelector("#adminApp .avatar").textContent = currentUser.name.charAt(0).toUpperCase();

    showPage("dashboard");
    renderAll();
}

function openCustomerPortal() {
    document.getElementById("adminApp").hidden = true;
    document.getElementById("customerApp").hidden = false;

    document.getElementById("customerHeaderName").textContent = currentUser.name;
    document.getElementById("customerWelcome").textContent = `Welcome, ${currentUser.name}!`;

    document.getElementById("profileName").value = currentUser.name;
    document.getElementById("profileEmail").value = currentUser.email;

    showCustomerPage("customerDashboard");
    renderAll();
}

// ADMIN NAVIGATION

function showPage(page, button) {
    const target = document.getElementById(page);
    if (!target) return;

    document.querySelectorAll("#adminApp .page").forEach(section => {
        section.classList.remove("active");
    });

    target.classList.add("active");

    document.querySelectorAll("#adminApp .nav-item").forEach(item => {
        item.classList.remove("active");
    });

    if (button) {
        button.classList.add("active");
    } else {
        const navButton = [...document.querySelectorAll("#adminApp .nav-item")].find(item =>
            item.getAttribute("onclick")?.includes(`'${page}'`)
        );
        if (navButton) navButton.classList.add("active");
    }

    const titles = {
        dashboard: "Dashboard",
        appointments: "Appointments",
        queue: "Queue Management",
        services: "Services",
        dentists: "Dentists",
        notifications: "Notifications",
        settings: "Settings"
    };

    document.getElementById("pageTitle").textContent = titles[page] || "QServe";
    document.getElementById("sidebar").classList.remove("open");

    renderAll();
}

function toggleSidebar() {
    document.getElementById("sidebar").classList.toggle("open");
}

// CUSTOMER NAVIGATION

function showCustomerPage(page, button) {
    const target = document.getElementById(page);
    if (!target) return;

    document.querySelectorAll(".customer-page").forEach(section => {
        section.classList.remove("active");
    });

    target.classList.add("active");

    document.querySelectorAll(".customer-nav-item").forEach(item => {
        item.classList.remove("active");
    });

    if (button) {
        button.classList.add("active");
    } else {
        const navButton = [...document.querySelectorAll(".customer-nav-item")].find(item =>
            item.getAttribute("onclick")?.includes(`'${page}'`)
        );
        if (navButton) navButton.classList.add("active");
    }

    if (page === "customerAccount") {
        document.getElementById("profileName").value = currentUser.name;
        document.getElementById("profileEmail").value = currentUser.email;
        document.getElementById("profilePassword").value = "";
        document.getElementById("profileConfirmPassword").value = "";
        document.getElementById("profileError").textContent = "";
        document.getElementById("profileSuccess").textContent = "";
    }

    renderCustomerPortal();
}

// DATE UTILITIES

function getToday() {
    const date = new Date();
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
}

function formatDate(value) {
    if (!value) return "";
    const [year, month, day] = value.split("-");
    return `${month}/${day}/${year}`;
}

// APPOINTMENT MODAL

function openModal() {
    document.getElementById("appointmentDate").min = getToday();
    document.getElementById("appointmentModal").classList.add("show");
}

function closeModal() {
    document.getElementById("appointmentModal").classList.remove("show");
}

document.getElementById("appointmentModal").addEventListener("click", function(e) {
    if (e.target === this) closeModal();
});

document.addEventListener("keydown", function(e) {
    if (e.key === "Escape") closeModal();
});

// CREATE ADMIN APPOINTMENT

document.getElementById("appointmentForm").addEventListener("submit", async function(e) {
    e.preventDefault();

    const name = document.getElementById("customerName").value.trim();
    const service = document.getElementById("serviceInput").value;
    const dentist = document.getElementById("adminDentistInput").value;
    const date = document.getElementById("appointmentDate").value;
    const time = document.getElementById("appointmentTime").value;

    if (!name || !service || !dentist || !date || !time) {
        alert("Please complete all fields.");
        return;
    }

    if (date < getToday()) {
        alert("Please select today or a future date.");
        return;
    }

    const duplicate = appointments.some(a =>
        (a.name.toLowerCase() === name.toLowerCase() || a.patientName.toLowerCase() === name.toLowerCase()) &&
        a.service === service &&
        a.dentist === dentist &&
        a.date === date &&
        a.time === time &&
        !["Rejected", "Cancelled"].includes(a.status)
    );

    if (duplicate) {
        alert("This appointment already exists.");
        return;
    }

    const apptData = {
        patientName: name,
        name: name,
        email: "",
        userId: "",
        service,
        dentist,
        date,
        time,
        status: "pending",
        queueNumber: 0
    };

    if (isFirebaseAvailable && db) {
        try {
            const docRef = db.collection("appointments").doc();
            apptData.id = docRef.id;
            await docRef.set(apptData);
        } catch (err) {
            console.error("Error saving appointment to Firestore:", err);
        }
    } else {
        apptData.id = Date.now().toString();
        appointments.push(apptData);
        saveData();
    }

    addNotification(`New appointment submitted by ${name}.`);

    this.reset();
    closeModal();
    renderAll();

    alert("Appointment successfully added.");
});

// CUSTOMER BOOKING

document.getElementById("customerBookingForm").addEventListener("submit", async function(e) {
    e.preventDefault();

    if (!currentUser || currentUser.role !== "customer") return;

    const service = document.getElementById("customerService").value;
    const dentist = document.getElementById("customerDentist").value;
    const date = document.getElementById("customerAppointmentDate").value;
    const time = document.getElementById("customerAppointmentTime").value;

    const error = document.getElementById("bookingError");
    error.textContent = "";

    if (!service || !dentist || !date || !time) {
        error.textContent = "Please complete all fields.";
        return;
    }

    if (date < getToday()) {
        error.textContent = "Please select today or a future date.";
        return;
    }

    const duplicate = appointments.some(a =>
        (a.email === currentUser.email || (currentUser.uid && a.userId === currentUser.uid)) &&
        a.service === service &&
        a.dentist === dentist &&
        a.date === date &&
        a.time === time &&
        !["Rejected", "Cancelled"].includes(a.status)
    );

    if (duplicate) {
        error.textContent = "You already have an appointment at this schedule.";
        return;
    }

    const apptData = {
        patientName: currentUser.name,
        name: currentUser.name,
        email: currentUser.email || "",
        userId: currentUser.uid || "",
        service,
        dentist,
        date,
        time,
        status: "pending",
        queueNumber: 0
    };

    if (isFirebaseAvailable && db) {
        try {
            const docRef = db.collection("appointments").doc();
            apptData.id = docRef.id;
            await docRef.set(apptData);
        } catch (err) {
            console.error("Error saving appointment to Firestore:", err);
        }
    } else {
        apptData.id = Date.now().toString();
        appointments.push(apptData);
        saveData();
    }

    addNotification(`New appointment submitted by ${currentUser.name}.`);

    this.reset();
    alert("Your appointment request has been submitted.");
    showCustomerPage("customerSchedule");
});

// CANCEL CUSTOMER APPOINTMENT

async function cancelAppointment(id) {
    if (!currentUser || currentUser.role !== "customer") return;

    const appointment = appointments.find(a =>
        String(a.id) === String(id) &&
        (a.email === currentUser.email || (currentUser.uid && a.userId === currentUser.uid))
    );

    if (!appointment) return;

    if (!["Pending", "Approved"].includes(appointment.status)) {
        alert("This appointment can no longer be cancelled.");
        return;
    }

    if (appointment.status === "Serving") {
        alert("You cannot cancel an appointment that is currently being served.");
        return;
    }

    if (!confirm("Are you sure you want to cancel this appointment?")) {
        return;
    }

    if (isFirebaseAvailable && db) {
        try {
            await db.collection("appointments").doc(String(id)).update({
                status: "cancelled",
                queueNumber: 0
            });
        } catch (err) {
            console.error("Error cancelling appointment in Firestore:", err);
        }
    } else {
        appointment.status = "Cancelled";
        appointment.queueNumber = 0;
        rebuildQueueFromAppointments();
        saveData();
        renderAll();
    }

    addNotification(`${appointment.name || appointment.patientName} cancelled an appointment.`);
    alert("Your appointment has been cancelled.");
}

// UPDATE APPOINTMENT STATUS

async function updateStatus(id, status) {
    const appointment = appointments.find(a => String(a.id) === String(id));
    if (!appointment) return;

    const allowed = ["Approved", "Rejected", "Cancelled", "approved", "rejected", "cancelled"];
    if (!allowed.includes(status)) return;

    const normStatus = normalizeStatus(status);
    if (appointment.status === normStatus) return;

    let updatedQueueNum = appointment.queueNumber;

    if (normStatus === "Approved" && !appointment.queueNumber) {
        const sameDayAppts = appointments.filter(a => a.date === appointment.date && a.queueNumber > 0);
        let maxQ = 0;
        sameDayAppts.forEach(a => { if (a.queueNumber > maxQ) maxQ = a.queueNumber; });
        updatedQueueNum = maxQ + 1;
    }

    if (["Rejected", "Cancelled"].includes(normStatus)) {
        updatedQueueNum = 0;
    }

    const dbStatus = normStatus.toLowerCase();

    if (isFirebaseAvailable && db) {
        try {
            await db.collection("appointments").doc(String(id)).update({
                status: dbStatus,
                queueNumber: updatedQueueNum
            });
        } catch (err) {
            console.error("Error updating status in Firestore:", err);
        }
    } else {
        appointment.status = normStatus;
        appointment.queueNumber = updatedQueueNum;
        rebuildQueueFromAppointments();
        saveData();
        renderAll();
    }

    addNotification(`${appointment.name || appointment.patientName}'s appointment is now ${normStatus}.`);
}

// APPOINTMENT TABLE

function appointmentHTML(a) {
    let actions = '<span class="muted">No actions</span>';

    if (a.status === "Pending") {
        actions = `
            <div class="actions">
                <button class="btn" onclick="updateStatus('${a.id}', 'Approved')">Approve</button>
                <button class="btn btn-danger" onclick="updateStatus('${a.id}', 'Rejected')">Reject</button>
            </div>
        `;
    } else if (a.status === "Approved") {
        actions = `
            <button class="btn btn-light" onclick="updateStatus('${a.id}', 'Cancelled')">Cancel</button>
        `;
    }

    return `
        <tr>
            <td>${escapeHTML(a.patientName || a.name)}</td>
            <td>${escapeHTML(a.service)}</td>
            <td>${escapeHTML(a.dentist || "Not assigned")}</td>
            <td>${escapeHTML(formatDate(a.date))}</td>
            <td>${escapeHTML(a.time)}</td>
            <td>
                <span class="status ${escapeHTML(a.status)}">
                    ${escapeHTML(a.status)}
                </span>
            </td>
            <td>${actions}</td>
        </tr>
    `;
}

function renderAppointments() {
    const container = document.getElementById("appointmentTable");

    if (!appointments.length) {
        container.innerHTML = '<div class="empty">No appointments found.</div>';
        return;
    }

    const sorted = [...appointments].sort((a, b) =>
        (a.date + a.time).localeCompare(b.date + b.time)
    );

    container.innerHTML = `
        <table>
            <thead>
                <tr>
                    <th>Customer</th>
                    <th>Service</th>
                    <th>Dentist</th>
                    <th>Date</th>
                    <th>Time</th>
                    <th>Status</th>
                    <th>Actions</th>
                </tr>
            </thead>

            <tbody>
                ${sorted.map(appointmentHTML).join("")}
            </tbody>
        </table>
    `;
}

// QUEUE MANAGEMENT

async function callNext() {
    const nextItem = queue.find(q => q.status === "Waiting");

    if (!nextItem) {
        alert("There are no waiting customers.");
        return;
    }

    const previousItem = queue.find(q => q.status === "Serving");

    if (isFirebaseAvailable && db) {
        try {
            const batch = db.batch();
            if (previousItem) {
                const prevRef = db.collection("appointments").doc(String(previousItem.appointmentId));
                batch.update(prevRef, { status: "done" });
            }
            const nextRef = db.collection("appointments").doc(String(nextItem.appointmentId));
            batch.update(nextRef, { status: "serving" });
            await batch.commit();
        } catch (err) {
            console.error("Error calling next customer in Firestore:", err);
        }
    } else {
        if (previousItem) {
            previousItem.status = "Completed";
            const oldAppt = appointments.find(a => String(a.id) === String(previousItem.appointmentId));
            if (oldAppt) oldAppt.status = "Completed";
        }
        nextItem.status = "Serving";
        currentQueue = nextItem.number;
        const appt = appointments.find(a => String(a.id) === String(nextItem.appointmentId));
        if (appt) appt.status = "Serving";

        saveData();
        renderAll();
    }

    addNotification(`Now serving ${nextItem.number} - ${nextItem.name}.`);
}

function renderQueue() {
    const waiting = queue.filter(q => q.status === "Waiting").length;

    document.getElementById("currentQueue").textContent = currentQueue;
    document.getElementById("queueCurrent").textContent = currentQueue;
    document.getElementById("waitingQueue").textContent = waiting;
    document.getElementById("queueWaiting").textContent = waiting;

    const container = document.getElementById("queueTable");

    if (!queue.length) {
        container.innerHTML = '<div class="empty">No customers in the queue.</div>';
        return;
    }

    container.innerHTML = `
        <table>
            <thead>
                <tr>
                    <th>Queue Number</th>
                    <th>Customer</th>
                    <th>Status</th>
                </tr>
            </thead>

            <tbody>
                ${queue.map(q => `
                    <tr>
                        <td><strong>${escapeHTML(q.number)}</strong></td>
                        <td>${escapeHTML(q.name)}</td>
                        <td>
                            <span class="status ${
                                q.status === "Serving"
                                    ? "Serving"
                                    : q.status === "Completed"
                                        ? "Completed"
                                        : ""
                            }">
                                ${escapeHTML(q.status)}
                            </span>
                        </td>
                    </tr>
                `).join("")}
            </tbody>
        </table>
    `;
}

// SERVICES

function renderServices() {
    const adminContainer = document.getElementById("serviceList");
    const customerContainer = document.getElementById("customerServiceList");
    const adminSelect = document.getElementById("serviceInput");
    const customerSelect = document.getElementById("customerService");

    if (!services.length) {
        adminContainer.innerHTML = '<div class="empty">No services available.</div>';
        customerContainer.innerHTML = '<div class="empty">No services available.</div>';
        adminSelect.innerHTML = '<option value="">No services available</option>';
        customerSelect.innerHTML = '<option value="">No services available</option>';
        return;
    }

    const cards = services.map(s => `
        <div class="service-card">
            <h3>${escapeHTML(s.name)}</h3>
            <p>${escapeHTML(s.description)}</p>
            <span class="status">Available</span>
        </div>
    `).join("");

    adminContainer.innerHTML = cards;
    customerContainer.innerHTML = cards;

    const options = `
        <option value="">Select a service</option>
        ${services.map(s => `
            <option value="${escapeHTML(s.name)}">
                ${escapeHTML(s.name)}
            </option>
        `).join("")}
    `;

    adminSelect.innerHTML = options;
    customerSelect.innerHTML = options;
}

// DENTIST MANAGEMENT

document.getElementById("dentistForm").addEventListener("submit", function(e) {
    e.preventDefault();

    const name = document.getElementById("dentistName").value.trim();
    const specialty = document.getElementById("dentistSpecialty").value.trim();

    if (!name || !specialty) return;

    if (dentists.some(d => d.name.toLowerCase() === name.toLowerCase())) {
        alert("This dentist already exists.");
        return;
    }

    dentists.push({
        id: Date.now(),
        name,
        specialty
    });

    addNotification(`${name} was added to the dentist list.`);
    saveData();
    this.reset();
    renderAll();

    alert("Dentist successfully added.");
});

function removeDentist(id) {
    const dentist = dentists.find(d => d.id === id);
    if (!dentist) return;

    const hasAppointments = appointments.some(a =>
        a.dentist === dentist.name &&
        !["Rejected", "Cancelled", "Completed"].includes(a.status)
    );

    if (hasAppointments) {
        alert("This dentist has active appointments and cannot be removed.");
        return;
    }

    if (!confirm(`Remove ${dentist.name} from the dentist list?`)) {
        return;
    }

    dentists = dentists.filter(d => d.id !== id);
    addNotification(`${dentist.name} was removed from the dentist list.`);

    saveData();
    renderAll();
}

function renderDentists() {
    const container = document.getElementById("dentistList");
    const customerSelect = document.getElementById("customerDentist");
    const adminSelect = document.getElementById("adminDentistInput");

    if (!dentists.length) {
        container.innerHTML = '<div class="empty">No dentists available.</div>';
        customerSelect.innerHTML = '<option value="">No dentists available</option>';
        adminSelect.innerHTML = '<option value="">No dentists available</option>';
        return;
    }

    container.innerHTML = dentists.map(d => `
        <div class="dentist-item">
            <div>
                <h4>${escapeHTML(d.name)}</h4>
                <p>${escapeHTML(d.specialty)}</p>
            </div>
            <button class="btn btn-danger" onclick="removeDentist(${d.id})">Remove</button>
        </div>
    `).join("");

    const options = `
        <option value="">Select a dentist</option>
        ${dentists.map(d => `
            <option value="${escapeHTML(d.name)}">
                ${escapeHTML(d.name)} - ${escapeHTML(d.specialty)}
            </option>
        `).join("")}
    `;

    customerSelect.innerHTML = options;
    adminSelect.innerHTML = options;
}

// EDIT CUSTOMER PROFILE

document.getElementById("profileForm").addEventListener("submit", function(e) {
    e.preventDefault();

    if (!currentUser || currentUser.role !== "customer") return;

    const name = document.getElementById("profileName").value.trim();
    const email = document.getElementById("profileEmail").value.trim().toLowerCase();
    const password = document.getElementById("profilePassword").value;
    const confirmPassword = document.getElementById("profileConfirmPassword").value;

    const error = document.getElementById("profileError");
    const success = document.getElementById("profileSuccess");

    error.textContent = "";
    success.textContent = "";

    if (!name || !email) {
        error.textContent = "Please complete your name and email.";
        return;
    }

    if (password && password.length < 8) {
        error.textContent = "New password must contain at least 8 characters.";
        return;
    }

    if (password !== confirmPassword) {
        error.textContent = "Passwords do not match.";
        return;
    }

    if (email === ADMIN_ACCOUNT.email.toLowerCase()) {
        error.textContent = "This email address cannot be used.";
        return;
    }

    const accounts = getAccounts();
    const account = accounts.find(user => user.email === currentUser.email);

    if (!account) {
        error.textContent = "Account could not be found.";
        return;
    }

    const emailExists = accounts.some(user =>
        user.email.toLowerCase() === email && user.id !== account.id
    );

    if (emailExists) {
        error.textContent = "This email address is already in use.";
        return;
    }

    const oldEmail = currentUser.email;

    account.name = name;
    account.email = email;

    if (password) {
        account.password = password;
    }

    appointments.forEach(a => {
        if (a.email === oldEmail) {
            a.email = email;
            a.patientName = name;
            a.name = name;
        }
    });

    queue.forEach(q => {
        if (q.email === oldEmail) {
            q.email = email;
            q.name = name;
        }
    });

    currentUser.name = name;
    currentUser.email = email;

    saveAccounts(accounts);
    saveSession(currentUser);
    saveData();

    document.getElementById("customerHeaderName").textContent = name;
    document.getElementById("customerWelcome").textContent = `Welcome, ${name}!`;
    document.getElementById("profilePassword").value = "";
    document.getElementById("profileConfirmPassword").value = "";

    success.textContent = "Your profile has been updated successfully.";
    renderAll();
});

// NOTIFICATIONS

function addNotification(message) {
    notifications.unshift({
        message,
        time: new Date().toLocaleString()
    });

    notifications = notifications.slice(0, 50);
}

function renderNotifications() {
    const container = document.getElementById("notificationList");

    if (!notifications.length) {
        container.innerHTML = '<div class="empty">No notifications available.</div>';
        return;
    }

    container.innerHTML = notifications.map(n => `
        <div class="notification">
            <strong>${escapeHTML(n.message)}</strong>
            <small>${escapeHTML(n.time)}</small>
        </div>
    `).join("");
}

// ADMIN DASHBOARD

function renderDashboard() {
    document.getElementById("totalAppointments").textContent = appointments.length;
    document.getElementById("pendingAppointments").textContent =
        appointments.filter(a => a.status === "Pending").length;
    document.getElementById("approvedAppointments").textContent =
        appointments.filter(a => a.status === "Approved").length;
    document.getElementById("totalServices").textContent = services.length;

    const recent = [...appointments]
        .sort((a, b) => (b.date + b.time).localeCompare(a.date + a.time))
        .slice(0, 5);

    document.getElementById("dashboardAppointments").innerHTML = recent.length ? `
        <div class="table-wrap">
            <table>
                <thead>
                    <tr>
                        <th>Customer</th>
                        <th>Date</th>
                        <th>Status</th>
                    </tr>
                </thead>

                <tbody>
                    ${recent.map(a => `
                        <tr>
                            <td>${escapeHTML(a.patientName || a.name)}</td>
                            <td>${escapeHTML(formatDate(a.date))}</td>
                            <td>
                                <span class="status ${escapeHTML(a.status)}">
                                    ${escapeHTML(a.status)}
                                </span>
                            </td>
                        </tr>
                    `).join("")}
                </tbody>
            </table>
        </div>
    ` : '<div class="empty">No recent appointments.</div>';
}

// CUSTOMER PORTAL

function getCustomerAppointments() {
    if (!currentUser) return [];

    return appointments
        .filter(a =>
            (a.email && a.email.toLowerCase() === currentUser.email.toLowerCase()) ||
            (currentUser.uid && a.userId === currentUser.uid)
        )
        .sort((a, b) => (b.date + b.time).localeCompare(a.date + a.time));
}

function renderCustomerPortal() {
    if (!currentUser || currentUser.role !== "customer") return;

    const mine = getCustomerAppointments();

    document.getElementById("customerTotalAppointments").textContent = mine.length;
    document.getElementById("customerPendingAppointments").textContent =
        mine.filter(a => a.status === "Pending").length;
    document.getElementById("customerApprovedAppointments").textContent =
        mine.filter(a => ["Approved", "Serving"].includes(a.status)).length;

    const recent = mine.slice(0, 5);

    document.getElementById("customerRecentAppointments").innerHTML = recent.length ? `
        <table>
            <thead>
                <tr>
                    <th>Service</th>
                    <th>Dentist</th>
                    <th>Date</th>
                    <th>Status</th>
                </tr>
            </thead>

            <tbody>
                ${recent.map(a => `
                    <tr>
                        <td>${escapeHTML(a.service)}</td>
                        <td>${escapeHTML(a.dentist || "Not assigned")}</td>
                        <td>${escapeHTML(formatDate(a.date))}</td>
                        <td>
                            <span class="status ${escapeHTML(a.status)}">
                                ${escapeHTML(a.status)}
                            </span>
                        </td>
                    </tr>
                `).join("")}
            </tbody>
        </table>
    ` : '<div class="empty">You have no appointments yet.</div>';

    document.getElementById("customerAppointmentTable").innerHTML = mine.length ? `
        <table>
            <thead>
                <tr>
                    <th>Service</th>
                    <th>Dentist</th>
                    <th>Date</th>
                    <th>Time</th>
                    <th>Queue</th>
                    <th>Status</th>
                    <th>Action</th>
                </tr>
            </thead>

            <tbody>
                ${mine.map(a => `
                    <tr>
                        <td>${escapeHTML(a.service)}</td>
                        <td>${escapeHTML(a.dentist || "Not assigned")}</td>
                        <td>${escapeHTML(formatDate(a.date))}</td>
                        <td>${escapeHTML(a.time)}</td>
                        <td>${escapeHTML(a.queue || (a.queueNumber ? `Q-${String(a.queueNumber).padStart(3, '0')}` : "—"))}</td>
                        <td>
                            <span class="status ${escapeHTML(a.status)}">
                                ${escapeHTML(a.status)}
                            </span>
                        </td>
                        <td>
                            ${
                                ["Pending", "Approved"].includes(a.status)
                                    ? `<button class="btn btn-danger" onclick="cancelAppointment('${a.id}')">Cancel</button>`
                                    : '<span class="muted">—</span>'
                            }
                        </td>
                    </tr>
                `).join("")}
            </tbody>
        </table>
    ` : '<div class="empty">You have no appointments yet.</div>';

    const active = mine.find(a =>
        a.queueNumber > 0 && ["Approved", "Serving"].includes(a.status)
    );

    const activeQueueStr = active ? `Q-${String(active.queueNumber).padStart(3, '0')}` : "—";
    document.getElementById("customerQueueNumber").textContent = activeQueueStr;
    document.getElementById("customerCurrentQueue").textContent = currentQueue;

    let message = "You currently have no active queue.";

    if (active) {
        if (active.status === "Approved") {
            message = "Your appointment is approved. Please wait for your turn.";
        } else if (active.status === "Serving") {
            message = "It is your turn! Please proceed.";
        }
    } else {
        const completed = mine.find(a => a.status === "Completed");
        if (completed) {
            message = "Your previous service has been completed.";
        }
    }

    document.getElementById("customerQueueStatus").textContent = message;
}

// HTML ESCAPING

function escapeHTML(value) {
    return String(value).replace(/[&<>"']/g, char => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
    })[char]);
}

// REFRESH ALL SECTIONS

function renderAll() {
    renderDashboard();
    renderAppointments();
    renderQueue();
    renderServices();
    renderDentists();
    renderNotifications();
    renderCustomerPortal();
}

// INITIALIZE APPLICATION

loadData();

const savedSession = getSession();

if (savedSession && ["admin", "customer"].includes(savedSession.role)) {
    currentUser = savedSession;
    document.getElementById("authSection").hidden = true;

    if (currentUser.role === "admin") {
        openAdminPortal();
    } else {
        openCustomerPortal();
    }
} else {
    document.getElementById("authSection").hidden = false;
    document.getElementById("adminApp").hidden = true;
    document.getElementById("customerApp").hidden = true;
}

renderAll();

// Start Firestore Realtime Listener
initFirestoreSync();
