
/* NAVIGATION */

function logout() {
    localStorage.removeItem("userEmail");
    window.location.href = "user_login.html";
}

function goProfile() {
    window.location.href = "profile.html";
}

function buildResume() {
    window.location.href = "templates.html";
}

/* FILE TEXT UPDATE */

function updateResumeText() {
    const file = document.getElementById("resumeFile").files[0];
    document.getElementById("resumeText").innerText =
        file ? file.name : "Choose Resume";
}

function resetResumeRecommendationsUI() {
    const matchedSection = document.getElementById("matchedInternshipsSection");
    const matchedContainer = document.getElementById("matchedInternshipsContainer");
    const resumeFile = document.getElementById("resumeFile");
    const resumeText = document.getElementById("resumeText");

    if (matchedSection) {
        matchedSection.classList.remove("show");
    }

    if (matchedContainer) {
        matchedContainer.innerHTML = '<div class="no-internships">Upload your resume to see matched internships based on your skills.</div>';
    }

    if (resumeFile) {
        resumeFile.value = "";
    }

    if (resumeText) {
        resumeText.innerText = "Choose Resume";
    }
}

function escapeHtml(value) {
    return (value || "")
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/\"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

const MAX_RESUME_FILE_SIZE = 10 * 1024 * 1024;
const ALLOWED_RESUME_EXTENSIONS = ["pdf", "doc", "docx", "txt"];

function getFileExtension(filename) {
    if (!filename || !filename.includes(".")) {
        return "";
    }
    return filename.split(".").pop().toLowerCase();
}

function isAllowedResumeFile(file) {
    const extension = getFileExtension(file?.name || "");
    return ALLOWED_RESUME_EXTENSIONS.includes(extension);
}

function renderResumeBasedInternships(payload) {
    const container = document.getElementById("matchedInternshipsContainer");
    if (!container) {
        return;
    }

    const recommendations = Array.isArray(payload?.recommendations) ? payload.recommendations : [];
    const message = payload?.message || "";

    if (recommendations.length === 0) {
        container.innerHTML = `<div class="no-internships">${escapeHtml(message || "No resume-based internships found. Try uploading a clearer resume.")}</div>`;
        return;
    }

    const sorted = recommendations
        .map((item) => ({ ...item, matchScore: Number(item.matchScore) || 0 }))
        .sort((a, b) => b.matchScore - a.matchScore);

    const cards = sorted.map((item) => {
        const title = escapeHtml(item.title || "Opportunity");
        const company = escapeHtml(item.company || "Unknown Company");
        const location = escapeHtml(item.location || "Location not specified");
        const type = escapeHtml(item.type || "Internship/Job");
        const source = escapeHtml(item.source || "Resume Matching");
        const score = Number(item.matchScore) || 0;
        const url = item.url && /^https?:\/\//i.test(item.url) ? item.url : "";
        const skills = Array.isArray(item.matchedSkills)
            ? item.matchedSkills.slice(0, 6).map((skill) => escapeHtml(skill)).join(", ")
            : "General match";

        return `
            <div class="internship-card">
                <div class="internship-header">
                    <div>
                        <h3 class="internship-title">${title}</h3>
                        <p class="internship-company">${company}</p>
                    </div>
                    <span class="match-badge">${score}% Match</span>
                </div>
                <p class="internship-description">${type} • ${location} • ${source}</p>
                <div class="internship-skills"><strong>Matched Skills:</strong> ${skills || "General match"}</div>
                <button class="apply-btn" ${url ? `onclick="window.open('${escapeHtml(url)}', '_blank', 'noopener,noreferrer')"` : "disabled"}>
                    ${url ? "Apply Now" : "No Link"}
                </button>
            </div>
        `;
    }).join("");

    container.innerHTML = `<div class="matched-internships-grid">${cards}</div>`;
}

function renderRecommendations(payload) {
    const listEl = document.getElementById("recommendationsList");
    const metaEl = document.getElementById("recommendationsMeta");
    const recommendations = Array.isArray(payload?.recommendations) ? payload.recommendations : [];
    const keywords = Array.isArray(payload?.extractedKeywords) ? payload.extractedKeywords : [];

    const metaParts = [];
    if (payload?.message) {
        metaParts.push(payload.message);
    }
    if (keywords.length > 0) {
        metaParts.push(`Keywords: ${keywords.slice(0, 12).join(", ")}`);
    }
    metaEl.innerText = metaParts.length > 0
        ? metaParts.join(" | ")
        : "Recommended opportunities based on your resume.";

    if (recommendations.length === 0) {
        listEl.innerHTML = '<div class="no-recommendations">No matching opportunities found right now.</div>';
        return;
    }

    listEl.innerHTML = recommendations.map((item) => {
        const title = escapeHtml(item.title || "Opportunity");
        const company = escapeHtml(item.company || "Unknown Company");
        const location = escapeHtml(item.location || "Location not specified");
        const type = escapeHtml(item.type || "Internship/Job");
        const source = escapeHtml(item.source || "Automation");
        const score = Number(item.matchScore) || 0;
        const skills = Array.isArray(item.matchedSkills) ? item.matchedSkills.slice(0, 6) : [];
        const url = item.url && /^https?:\/\//i.test(item.url) ? item.url : "";

        const skillsHtml = skills.length > 0
            ? `<div class="recommendation-skills">${skills.map((skill) => `<span>${escapeHtml(skill)}</span>`).join("")}</div>`
            : "";

        const actionHtml = url
            ? `<button class="recommendation-open-btn" onclick="window.open('${escapeHtml(url)}', '_blank', 'noopener,noreferrer')">Open</button>`
            : `<button class="recommendation-open-btn disabled" disabled>No Link</button>`;

        return `
            <div class="recommendation-card">
                <div class="recommendation-top">
                    <div>
                        <div class="recommendation-title">${title}</div>
                        <div class="recommendation-company">${company}</div>
                    </div>
                    <div class="recommendation-score">${score}% Match</div>
                </div>
                <div class="recommendation-meta-row">${type} • ${location} • ${source}</div>
                ${skillsHtml}
                <div class="recommendation-actions">${actionHtml}</div>
            </div>
        `;
    }).join("");
}

async function uploadResume() {
    const fileInput = document.getElementById("resumeFile");
    const uploadButton = document.querySelector("button[onclick='uploadResume()']");
    const file = fileInput?.files?.[0];

    if (!file) {
        alert("Please choose your resume first.");
        return;
    }

    if (!isAllowedResumeFile(file)) {
        alert("Please upload a valid resume file: PDF, DOC, DOCX, or TXT.");
        return;
    }

    if (file.size > MAX_RESUME_FILE_SIZE) {
        alert("Resume file exceeds 10 MB limit.");
        return;
    }

    if (uploadButton) {
        uploadButton.disabled = true;
        uploadButton.innerText = "Processing...";
    }

    const matchedSection = document.getElementById("matchedInternshipsSection");
    const matchedContainer = document.getElementById("matchedInternshipsContainer");

    try {
        if (matchedSection) {
            matchedSection.classList.add("show");
        }
        if (matchedContainer) {
            matchedContainer.innerHTML = '<div class="no-internships">Analyzing resume and finding internships...</div>';
        }

        const formData = new FormData();
        formData.append("resume", file);

        const response = await fetch(`http://localhost:8089/api/students/resume/recommendations?email=${encodeURIComponent(userEmail)}`, {
            method: "POST",
            body: formData
        });

        const payload = await response.json();

        // Check if the response indicates validation errors
        if (payload?.recommendations && payload.recommendations.length === 0 && payload?.message) {
            // This is a validation error from backend (not a resume)
            if (matchedContainer) {
                matchedContainer.innerHTML = `<div class="no-internships validation-error">${escapeHtml(payload.message)}</div>`;
            }
            return;
        }

        if (!response.ok) {
            throw new Error(payload?.message || `Resume upload failed (HTTP ${response.status})`);
        }

        renderResumeBasedInternships(payload);
        
        // Scroll to matched internships section
        if (matchedSection) {
            matchedSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    } catch (error) {
        console.error("Resume processing error:", error);
        if (matchedContainer) {
            const errorMessage = error.message || "Failed to process resume. Please try a valid resume file.";
            matchedContainer.innerHTML = `<div class="no-internships validation-error">${escapeHtml(errorMessage)}</div>`;
        }
    } finally {
        if (uploadButton) {
            uploadButton.disabled = false;
            uploadButton.innerText = "Upload & Get Internships";
        }
    }
}

async function loadMatchedInternships() {
    const container = document.getElementById("matchedInternshipsContainer");
    if (!container) {
        console.error("Matched internships container not found in DOM");
        return;
    }

    // Show loading state
    container.innerHTML = '<div class="no-internships">Loading matched internships...</div>';

    try {
        const response = await fetch(`http://localhost:8089/api/students/internships/match?email=${userEmail}`);
        const matchedInternships = await response.json();

        if (!response.ok || !Array.isArray(matchedInternships) || matchedInternships.length === 0) {
            console.warn("No matched internships found");
            // Show no match message and load all available internships
            container.innerHTML = '<div class="no-match-message">No internships matched your resume skills. Here are all available opportunities:</div>';
            await loadAllInternships();
            return;
        }

        renderMatchedInternships(matchedInternships);
    } catch (error) {
        console.error("Error loading matched internships:", error);
        container.innerHTML = '<div class="no-internships">Failed to load internships. Please try again.</div>';
    }
}

async function loadAllInternships() {
    const container = document.getElementById("matchedInternshipsContainer");
    
    try {
        const response = await fetch(`http://localhost:8089/api/students/internships/all`);
        const allInternships = await response.json();

        if (!response.ok || !Array.isArray(allInternships) || allInternships.length === 0) {
            container.innerHTML += '<div class="no-internships">No internships available at the moment.</div>';
            return;
        }

        renderAllInternships(allInternships);
    } catch (error) {
        console.error("Error loading all internships:", error);
        container.innerHTML += '<div class="no-internships">Failed to load available internships.</div>';
    }
}

function renderAllInternships(internships) {
    const container = document.getElementById("matchedInternshipsContainer");
    
    const html = internships.map((internship) => {
        const title = escapeHtml(internship.title || "Internship");
        const company = escapeHtml(internship.company || "Unknown");
        const description = escapeHtml((internship.description || "").substring(0, 150));
        const location = escapeHtml(internship.location || "Not specified");
        const duration = escapeHtml(internship.duration || "Duration not specified");
        const stipend = escapeHtml(internship.stipend || "Stipend not specified");
        const url = internship.applicationLink || internship.applyLink || "";

        return `
            <div class="internship-card available">
                <div class="internship-header">
                    <div>
                        <h3 class="internship-title">${title}</h3>
                        <p class="internship-company">${company}</p>
                    </div>
                    <span class="available-badge">Available</span>
                </div>
                <p class="internship-description">${description}${(internship.description || "").length > 150 ? "..." : ""}</p>
                <div class="internship-details">
                    <span class="detail-item">📍 ${location}</span>
                    <span class="detail-item">⏱️ ${duration}</span>
                    <span class="detail-item">💰 ${stipend}</span>
                </div>
                <button class="apply-btn" onclick="window.open('${escapeHtml(url || '#')}', '_blank', 'noopener,noreferrer')">Apply Now</button>
            </div>
        `;
    }).join("");

    container.innerHTML += `<div class="available-internships-grid">${html}</div>`;
}

function renderMatchedInternships(internships) {
    const container = document.getElementById("matchedInternshipsContainer");
    if (!container) {
        console.error("Matched internships container not found in DOM");
        return;
    }

    if (!internships || internships.length === 0) {
        container.innerHTML = '<div class="no-internships">No matching internships found based on your resume.</div>';
        return;
    }

    // Sort by match score
    const sorted = internships.sort((a, b) => (b.matchScore || 0) - (a.matchScore || 0));

    const html = sorted.map((internship) => {
        const title = escapeHtml(internship.title || "Internship");
        const company = escapeHtml(internship.company || "Unknown");
        const description = escapeHtml((internship.description || "").substring(0, 150));
        const location = escapeHtml(internship.location || "Not specified");
        const duration = escapeHtml(internship.duration || "Duration not specified");
        const stipend = escapeHtml(internship.stipend || "Stipend not specified");
        const matchScore = internship.matchScore || 0;
        const matchedSkills = escapeHtml(internship.matchedSkills || "General match");
        const url = internship.applicationLink;

        return `
            <div class="internship-card">
                <div class="internship-header">
                    <div>
                        <h3 class="internship-title">${title}</h3>
                        <p class="internship-company">${company}</p>
                    </div>
                    <span class="match-badge">${matchScore}% Match</span>
                </div>
                <p class="internship-description">${description}${(internship.description || "").length > 150 ? "..." : ""}</p>
                <div class="internship-details">
                    <span class="detail-item">📍 ${location}</span>
                    <span class="detail-item">⏱️ ${duration}</span>
                    <span class="detail-item">💰 ${stipend}</span>
                </div>
                <div class="internship-skills">
                    <strong>Matched Skills:</strong> ${matchedSkills}
                </div>
                <button class="apply-btn" onclick="window.open('${escapeHtml(url || '#')}', '_blank', 'noopener,noreferrer')">Apply Now</button>
            </div>
        `;
    }).join("");

    container.innerHTML = `<div class="matched-internships-grid">${html}</div>`;
}

// Raw text toggle handler
document.addEventListener('DOMContentLoaded', function() {
    resetResumeRecommendationsUI();
});

/* WELCOME ANIMATION */

function animateWelcome(name) {

    const container = document.getElementById("welcomeContainer");
    container.innerHTML = "";

    const text = "Welcome ";

    text.split("").forEach(letter => {
        const span = document.createElement("span");
        span.innerText = letter;
        span.style.display = "inline-block";
        span.style.opacity = "0";
        span.style.transform = "translateY(40px)";

        if (letter.toLowerCase() === "c" || letter.toLowerCase() === "m") {
            span.classList.add("flip-letter");
        }

        container.appendChild(span);
    });

    const nameSpan = document.createElement("span");
    nameSpan.innerText = name + " 👋";
    nameSpan.classList.add("username-animated");
    nameSpan.style.opacity = "0";
    nameSpan.style.transform = "translateY(40px)";
    container.appendChild(nameSpan);

    // Welcome message timeline - FAST and SMOOTH
    const welcomeTl = gsap.timeline();

    welcomeTl.to("#welcomeContainer span:not(.username-animated)", {
        opacity: 1,
        y: 0,
        duration: 0.4,
        stagger: 0.02,
        ease: "power2.out"
    });

    welcomeTl.fromTo(".flip-letter",
        { rotationY: 180 },
        { rotationY: 0, duration: 0.5, ease: "back.out(1.7)" },
        0
    );

    welcomeTl.to(".username-animated", {
        opacity: 1,
        y: -8,
        duration: 0.4,
        ease: "power2.out"
    });

    welcomeTl.to(".username-animated", {
        y: 0,
        duration: 0.3,
        ease: "elastic.out(1, 0.5)"
    });

    // Cards spawn AFTER welcome animation completes
    welcomeTl.add(() => {
        animateCards();
    });
}

function animateCards() {
    gsap.to(".card", {
        opacity: 1,
        y: 0,
        duration: 0.8,
        stagger: 0.2,
        ease: "power3.out"
    });

    // Animate social section after cards
    gsap.to("#socialSection", {
        opacity: 1,
        y: 0,
        duration: 0.6,
        ease: "power2.out",
        delay: 0.6
    });
}

/* LOAD USER FROM MYSQL */

// Store social profiles in memory
let socialProfiles = [];
let studentProjects = [];
let barChartInstance;
let lineChartInstance;

function selectPlatform(platform, key) {
    // Removed - users add profiles from Profile page instead
}

function addSocialProfileNew() {
    // Removed - users add profiles from Profile page instead
}

function removeSocialProfile(platform) {
    // Removed - users remove profiles from Profile page instead
}

function resetAnalyticsCharts() {
    if (barChartInstance) {
        barChartInstance.destroy();
        barChartInstance = null;
    }

    if (lineChartInstance) {
        lineChartInstance.destroy();
        lineChartInstance = null;
    }
}

function renderAnalyticsHighlights(highlights, fallbackStats) {
    const statsContainer = document.getElementById("analyticsStats");
    statsContainer.innerHTML = "";

    const entries = highlights && typeof highlights === "object"
        ? Object.entries(highlights)
        : [];

    const safeFallback = fallbackStats || {
        "Problems Attempted": "0",
        "Contest Stats": "0",
        "Activity Streak": "0 days"
    };

    const cards = entries.length > 0 ? entries : Object.entries(safeFallback);

    cards.forEach(([label, value]) => {
        const card = document.createElement("div");
        card.className = "analytics-stat";
        card.innerHTML = `
            <div class="analytics-stat-label">${label}</div>
            <div class="analytics-stat-value">${value ?? "0"}</div>
        `;
        statsContainer.appendChild(card);
    });
}

function getPlatformFallbackHighlights(platformKey) {
    if (platformKey === "linkedin") {
        return {
            "Username": "N/A",
            "Connections": "0",
            "Posts": "0"
        };
    }

    if (platformKey === "leetcode" || platformKey === "codechef" || platformKey === "hackerrank") {
        return {
            "Username": "N/A",
            "Problems Solved": "0",
            "Contests Attempted": "0",
            "Highest Rating": "0",
            "Current Rating": "0"
        };
    }

    if (platformKey === "github") {
        return {
            "Username": "N/A",
            "Repositories": "0",
            "Followers": "0",
            "Commits": "0",
            "Activity Streak": "0 days"
        };
    }

    return {
        "Problems Attempted": "0",
        "Contest Stats": "0",
        "Activity Streak": "0 days"
    };
}

function renderLinkedInPostsInfo(platformKey, source, message) {
    const postsBox = document.getElementById("analyticsPosts");
    const postsBody = document.getElementById("analyticsPostsBody");

    if (platformKey !== "linkedin") {
        postsBox.style.display = "none";
        postsBody.innerText = "";
        return;
    }

    postsBox.style.display = "block";
    if (source === "LIVE") {
        postsBody.innerText = message || "LinkedIn posts analytics loaded.";
    } else {
        postsBody.innerText = message || "LinkedIn does not provide public post-content API access. Connections/posts count is shown when available.";
    }
}

function renderLinkedInProfile(data, platformKey) {
    const profileBox = document.getElementById("linkedinProfile");
    const avatar = document.getElementById("linkedinAvatar");
    const identity = document.getElementById("linkedinIdentity");

    if (platformKey !== "linkedin") {
        profileBox.style.display = "none";
        identity.innerText = "N/A";
        avatar.src = "";
        return;
    }

    const extractLinkedInIdFromUrl = (url) => {
        if (!url || typeof url !== "string") return "";
        try {
            const normalized = /^https?:\/\//i.test(url) ? url : `https://${url}`;
            const parsed = new URL(normalized);
            const parts = parsed.pathname.split("/").filter(Boolean);
            const inIndex = parts.findIndex((part) => part.toLowerCase() === "in");
            if (inIndex !== -1 && parts[inIndex + 1]) {
                return decodeURIComponent(parts[inIndex + 1]);
            }
            return "";
        } catch (error) {
            return "";
        }
    };

    const linkedInIdFromUrl = extractLinkedInIdFromUrl(data?.profileUrl);

    profileBox.style.display = "flex";
    identity.innerText = linkedInIdFromUrl || data?.username || "N/A";
    avatar.src = data?.profilePhotoUrl || "https://ui-avatars.com/api/?name=LinkedIn+User&background=E2E8F0&color=1E293B&size=128";
}

function toggleChartCards(showCharts) {
    document.getElementById("barChartCard").style.display = showCharts ? "block" : "none";
    document.getElementById("lineChartCard").style.display = showCharts ? "block" : "none";
}

function getHeatmapColor(value, max) {
    if (!value || value <= 0) return "#e5e7eb";
    const ratio = max > 0 ? value / max : 0;
    if (ratio <= 0.25) return "#bbf7d0";
    if (ratio <= 0.5) return "#86efac";
    if (ratio <= 0.75) return "#4ade80";
    return "#15803d";
}

function sortHeatmapEntries(heatmapData) {
    if (!heatmapData || typeof heatmapData !== "object") {
        return [];
    }

    return Object.entries(heatmapData)
        .filter(([date]) => /^\d{4}-\d{2}-\d{2}$/.test(date))
        .sort(([dateA], [dateB]) => dateA.localeCompare(dateB));
}

function buildGithubGrowthFromHeatmap(heatmapData) {
    const entries = sortHeatmapEntries(heatmapData);
    if (entries.length === 0) {
        return { labels: [], values: [] };
    }

    const monthly = new Map();

    entries.forEach(([date, rawValue]) => {
        const value = Number(rawValue) || 0;
        const monthKey = date.slice(0, 7);
        monthly.set(monthKey, (monthly.get(monthKey) || 0) + value);
    });

    const monthKeys = Array.from(monthly.keys());
    const lastKeys = monthKeys.slice(-6);

    const labels = lastKeys.map((key) => {
        const [year, month] = key.split("-");
        const date = new Date(Number(year), Number(month) - 1, 1);
        return date.toLocaleString("en-US", { month: "short", year: "2-digit" });
    });

    const values = lastKeys.map((key) => monthly.get(key) || 0);

    return { labels, values };
}

function renderPlatformHeatmap(platformKey, heatmapData) {
    const heatmapBox = document.getElementById("analyticsHeatmap");
    const heatmapGrid = document.getElementById("analyticsHeatmapGrid");
    const heatmapLegend = document.getElementById("analyticsHeatmapLegend");

    if (platformKey !== "github" && platformKey !== "leetcode") {
        heatmapBox.style.display = "none";
        heatmapGrid.innerHTML = "";
        heatmapLegend.innerText = "";
        return;
    }

    const entries = sortHeatmapEntries(heatmapData);

    heatmapBox.style.display = "block";
    heatmapGrid.innerHTML = "";

    const counts = entries.map(([, value]) => Number(value) || 0);
    const max = counts.length > 0 ? Math.max(...counts) : 0;
    const total = counts.reduce((sum, value) => sum + value, 0);

    const firstDateObj = entries.length > 0 ? new Date(entries[0][0] + "T00:00:00") : null;
    const firstDayIndex = firstDateObj ? firstDateObj.getDay() : 0;

    for (let i = 0; i < firstDayIndex; i++) {
        const emptyCell = document.createElement("div");
        emptyCell.className = "analytics-heatmap-cell";
        emptyCell.style.background = "transparent";
        emptyCell.style.border = "none";
        emptyCell.style.pointerEvents = "none";
        emptyCell.style.gridColumn = "1";
        emptyCell.style.gridRow = String(i + 1);
        heatmapGrid.appendChild(emptyCell);
    }

    entries.forEach(([date, rawValue], index) => {
        const value = Number(rawValue) || 0;
        const cell = document.createElement("div");
        cell.className = "analytics-heatmap-cell";
        cell.style.background = getHeatmapColor(value, max);

        const position = index + firstDayIndex;
        const column = Math.floor(position / 7) + 1;
        const row = (position % 7) + 1;
        cell.style.gridColumn = String(column);
        cell.style.gridRow = String(row);

        cell.title = `${date}: ${value}`;
        heatmapGrid.appendChild(cell);
    });

    if (entries.length === 0) {
        heatmapLegend.innerText = "No heatmap data available for last year.";
        return;
    }

    const firstDate = entries[0][0];
    const lastDate = entries[entries.length - 1][0];

    const formatMonthYear = (dateText) => {
        const date = new Date(dateText + "T00:00:00");
        return date.toLocaleString("en-US", { month: "short", year: "numeric" });
    };

    const rangeText = `${formatMonthYear(firstDate)} - ${formatMonthYear(lastDate)}`;

    if (platformKey === "github") {
        heatmapLegend.innerText = `${rangeText} | Total commits: ${total}`;
    } else {
        heatmapLegend.innerText = `${rangeText} | Total submissions: ${total}`;
    }
}

function renderSolvedQuestions(platformKey, solvedQuestions, solvedQuestionsNote) {
    const questionBox = document.getElementById("analyticsQuestions");
    const questionNote = document.getElementById("analyticsQuestionsNote");
    const questionList = document.getElementById("analyticsQuestionsList");

    if (platformKey !== "leetcode" && platformKey !== "codechef" && platformKey !== "hackerrank") {
        questionBox.style.display = "none";
        questionNote.innerText = "";
        questionList.innerHTML = "";
        return;
    }

    const list = Array.isArray(solvedQuestions) ? solvedQuestions : [];
    questionBox.style.display = "block";
    questionNote.innerText = solvedQuestionsNote || "Solved questions";
    questionList.innerHTML = "";

    if (list.length === 0) {
        const li = document.createElement("li");
        li.innerText = "No solved question list available for this profile right now.";
        questionList.appendChild(li);
        return;
    }

    list.slice(0, 20).forEach((item) => {
        const li = document.createElement("li");
        li.innerText = item;
        questionList.appendChild(li);
    });
}

async function openAnalytics(platformKey, platformName) {
    const modal = document.getElementById("analyticsModal");
    const info = document.getElementById("analyticsInfo");

    modal.style.display = "flex";
    document.getElementById("analyticsTitle").innerText = `${platformName} Analytics`;
    renderAnalyticsHighlights(null, getPlatformFallbackHighlights(platformKey));
    renderLinkedInPostsInfo(platformKey, null, null);
    renderLinkedInProfile(null, platformKey);
    toggleChartCards(platformKey !== "linkedin");
    renderPlatformHeatmap(platformKey, null);
    renderSolvedQuestions(platformKey, null, null);
    info.innerText = "Fetching real analytics...";

    resetAnalyticsCharts();

    try {
        const response = await fetch(
            `http://localhost:8089/api/students/social/analytics?email=${encodeURIComponent(userEmail)}&platform=${encodeURIComponent(platformKey)}`
        );

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const data = await response.json();

        const barLabels = Array.isArray(data.barLabels) ? data.barLabels : [];
        const barData = Array.isArray(data.barData) ? data.barData : [];
        let lineLabels = Array.isArray(data.lineLabels) ? data.lineLabels : [];
        let lineData = Array.isArray(data.lineData) ? data.lineData : [];
        const heatmap = data.heatmap && typeof data.heatmap === "object" ? data.heatmap : null;
        const solvedQuestions = Array.isArray(data.solvedQuestions) ? data.solvedQuestions : [];

        if (platformKey === "github") {
            const githubGrowth = buildGithubGrowthFromHeatmap(heatmap);
            if (githubGrowth.labels.length > 0 && githubGrowth.values.length > 0) {
                lineLabels = githubGrowth.labels;
                lineData = githubGrowth.values;
            }
        }

        const platformFallback = getPlatformFallbackHighlights(platformKey);
        renderAnalyticsHighlights(data.highlights, platformFallback);
        renderLinkedInPostsInfo(platformKey, data.source, data.lastPost || data.message);
        renderLinkedInProfile(data, platformKey);

        const sourceText = data.source ? `Source: ${data.source}` : "Source: API";
        const messageText = data.message ? ` | ${data.message}` : "";
        info.innerText = `${sourceText}${messageText}`;
        renderPlatformHeatmap(platformKey, heatmap);
        renderSolvedQuestions(platformKey, solvedQuestions, data.solvedQuestionsNote);

        const shouldShowCharts = platformKey !== "linkedin";
        toggleChartCards(shouldShowCharts);

        if (shouldShowCharts && barLabels.length > 0 && barData.length > 0) {
            barChartInstance = new Chart(document.getElementById("barChart"), {
                type: "bar",
                data: {
                    labels: barLabels,
                    datasets: [{
                        label: `${platformName} Metrics`,
                        data: barData,
                        backgroundColor: ["#fbbf24", "#fb923c", "#f97316", "#fdba74", "#fb7185"],
                        borderRadius: 8
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: { display: true }
                    },
                    scales: {
                        y: {
                            beginAtZero: true,
                            ticks: { precision: 0 }
                        }
                    }
                }
            });
        }

        if (shouldShowCharts && lineLabels.length > 0 && lineData.length > 0) {
            lineChartInstance = new Chart(document.getElementById("lineChart"), {
                type: "line",
                data: {
                    labels: lineLabels,
                    datasets: [{
                        label: platformKey === "github" ? "GitHub Monthly Commits" : `${platformName} Growth`,
                        data: lineData,
                        fill: false,
                        tension: 0.35,
                        borderColor: "#ff8c05",
                        backgroundColor: "#ff8c05",
                        pointRadius: 4
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: { display: true }
                    },
                    scales: {
                        y: {
                            beginAtZero: true,
                            ticks: { precision: 0 }
                        }
                    }
                }
            });
        }

        if (barLabels.length === 0 && lineLabels.length === 0) {
            info.innerText = "No real analytics data available for this platform/profile yet.";
        }
    } catch (error) {
        console.error("Error fetching analytics:", error);
        info.innerText = "Failed to fetch real analytics. Check backend server/API availability and profile link validity.";
        renderAnalyticsHighlights(null, getPlatformFallbackHighlights(platformKey));
        renderLinkedInPostsInfo(platformKey, null, null);
        renderLinkedInProfile(null, platformKey);
        toggleChartCards(platformKey !== "linkedin");
        renderPlatformHeatmap(platformKey, null);
        renderSolvedQuestions(platformKey, null, null);
        resetAnalyticsCharts();
    }
}

function closeAnalytics() {
    document.getElementById("analyticsModal").style.display = "none";
}

function normalizeSocialUrl(platformKey, rawValue) {
    const value = (rawValue || "").trim();
    if (!value) return "";

    if (/^https?:\/\//i.test(value)) {
        return value;
    }

    const clean = value.replace(/^@/, "").replace(/^\/+|\/+$/g, "");

    if (/\./.test(clean)) {
        return `https://${clean}`;
    }

    if (platformKey === "linkedin") return `https://www.linkedin.com/in/${clean}`;
    if (platformKey === "github") return `https://github.com/${clean}`;
    if (platformKey === "leetcode") return `https://leetcode.com/${clean}`;
    if (platformKey === "codechef") return `https://www.codechef.com/users/${clean}`;
    if (platformKey === "hackerrank") return `https://www.hackerrank.com/${clean}`;

    return `https://${clean}`;
}

function openProfileInBrowser(platformKey) {
    const profile = socialProfiles.find((item) => item.platform && item.platform.toLowerCase() === platformKey);
    const url = normalizeSocialUrl(platformKey, profile?.url || "");

    if (!url) {
        alert("Profile link not found. Please add it from Profile page first.");
        return;
    }

    window.open(url, "_blank", "noopener,noreferrer");
}

function openProjectLink(url) {
    if (!url) {
        return;
    }

    let resolved = url;
    try {
        resolved = decodeURIComponent(url);
    } catch (e) {
        resolved = url;
    }

    window.open(resolved, "_blank", "noopener,noreferrer");
}

function renderProjects(projects) {
    const container = document.getElementById("projectsContainer");
    if (!container) {
        return;
    }

    if (!Array.isArray(projects) || projects.length === 0) {
        container.innerHTML = '<div class="no-projects-msg">No projects added yet. Add your deployed links in Profile.</div>';
        return;
    }

    const sorted = [...projects]
        .sort((a, b) => Number(Boolean(b.featured)) - Number(Boolean(a.featured)))
        .slice(0, 6);

    container.innerHTML = `<div class="projects-grid">${sorted.map((project) => {
        const title = escapeHtml(project.title || "Untitled Project");
        const deployed = project.deployedLink || "";
        const github = project.githubLink || "";
        const encodedDeployed = deployed ? encodeURIComponent(deployed) : "";
        const encodedGithub = github ? encodeURIComponent(github) : "";
        const techStack = escapeHtml(project.techStack || "Tech stack not specified");
        const description = escapeHtml(project.description || "No description provided yet.");
        const featuredBadge = project.featured
            ? '<span class="project-featured">Featured</span>'
            : '';

        return `
            <div class="project-card">
                <div class="project-top">
                    <div class="project-title-wrap">
                        <h3 class="project-title">${title}</h3>
                        ${featuredBadge}
                    </div>
                </div>

                <div class="project-tech">${techStack}</div>
                <p class="project-description">${description}</p>

                <div class="project-actions">
                    <button class="project-btn" ${deployed ? `onclick="openProjectLink('${encodedDeployed}')"` : "disabled"}>Live Demo</button>
                    <button class="project-btn secondary" ${github ? `onclick="openProjectLink('${encodedGithub}')"` : "disabled"}>GitHub</button>
                </div>
            </div>
        `;
    }).join("")}</div>`;
}

function displayAddedProfiles() {
    const container = document.getElementById('profilesContainer');
    
    // Create profile map for quick lookup
    const profileMap = {};
    socialProfiles.forEach(profile => {
        profileMap[profile.platform.toLowerCase()] = profile.url;
    });

    // Define all 5 platforms
    const platforms = [
        { name: 'LinkedIn', key: 'linkedin' },
        { name: 'GitHub', key: 'github' },
        { name: 'LeetCode', key: 'leetcode' },
        { name: 'CodeChef', key: 'codechef' },
        { name: 'HackerRank', key: 'hackerrank' }
    ];

    let html = '<div class="added-profiles">';
    platforms.forEach(platform => {
        const url = normalizeSocialUrl(platform.key, profileMap[platform.key] || '');
        const status = url ? 'Profile connected' : 'Not added yet';
        const actionLabel = 'Open Profile';
        const actionHandler = `openProfileInBrowser('${platform.key}')`;
        
        html += `
            <div class="profile-card">
                <div class="profile-title">${platform.name}</div>
                <div class="profile-link">
                    <span class="profile-status">${status}</span>
                </div>
                <button class="view-analytics-btn" onclick="${actionHandler}">${actionLabel}</button>
            </div>
        `;
    });
    html += '</div>';
    
    container.innerHTML = html;
}

async function loadSocialProfiles() {
    try {
        const response = await fetch(`http://localhost:8089/api/students/social?email=${userEmail}`);
        const data = await response.json();
        
        if (data && Array.isArray(data)) {
            socialProfiles = data;
            displayAddedProfiles();
        }
    } catch (error) {
        console.error("Error loading social profiles:", error);
    }
}

async function loadProjects() {
    try {
        const response = await fetch(`http://localhost:8089/api/students/projects?email=${encodeURIComponent(userEmail)}`);
        if (!response.ok) {
            studentProjects = [];
            renderProjects([]);
            return;
        }

        const data = await response.json();
        studentProjects = Array.isArray(data) ? data : [];
        renderProjects(studentProjects);
    } catch (error) {
        console.error("Error loading projects:", error);
        studentProjects = [];
        renderProjects([]);
    }
}

/* ================= GLOBAL CONTESTS ================= */

const CONTEST_PLATFORMS = ["CODECHEF", "LEETCODE", "CODEFORCES"];

// Call kontests.net directly from the browser (public CORS-enabled API)
const CONTEST_API_URLS = {
    CODECHEF:   "https://kontests.net/api/v1/code_chef",
    LEETCODE:   "https://kontests.net/api/v1/leet_code",
    CODEFORCES: "https://kontests.net/api/v1/codeforces"
};

// Direct links shown when APIs are unavailable
const PLATFORM_FALLBACK_URLS = {
    CODECHEF:   "https://www.codechef.com/contests",
    LEETCODE:   "https://leetcode.com/contest/",
    CODEFORCES: "https://codeforces.com/contests"
};

/**
 * Fetch contests via the backend (avoids CORS). Backend tries kontests.net
 * first, then falls back to each platform's own API.
 * Returns array of contests or null on failure.
 */
async function fetchPlatformContests(platform) {
    try {
        const ctrl = new AbortController();
        const tid = setTimeout(() => ctrl.abort(), 15000);
        const res = await fetch(
            `http://localhost:8089/api/contests?platform=${encodeURIComponent(platform)}`,
            { signal: ctrl.signal }
        );
        clearTimeout(tid);
        if (res.ok) {
            const data = await res.json();
            if (Array.isArray(data) && data.length > 0) return data;
        }
    } catch (e) { /* backend offline */ }
    return null;
}

function formatContestTime(isoString) {
    if (!isoString) return "Time TBD";
    try {
        const d = new Date(isoString);
        return d.toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" });
    } catch (e) {
        return isoString;
    }
}

function getContestBadge(isoStart) {
    if (!isoStart) return "";
    try {
        const diff = new Date(isoStart) - Date.now();
        const hours = diff / 3600000;
        if (hours < 0)  return "";
        if (hours < 3)  return '<span class="contest-badge badge-soon">Starting very soon</span>';
        if (hours < 24) return '<span class="contest-badge badge-starting">Today</span>';
        return '<span class="contest-badge badge-upcoming">Upcoming</span>';
    } catch (e) {
        return "";
    }
}

function renderContestList(platform, contests) {
    const listEl = document.getElementById(`contestList-${platform}`);
    const statusEl = document.getElementById(`contestStatus-${platform}`);
    if (!listEl) return;

    if (!Array.isArray(contests) || contests.length === 0) {
        listEl.innerHTML = '<div class="no-contests-msg">No upcoming contests found.</div>';
        if (statusEl) statusEl.textContent = "No upcoming contests";
        return;
    }

    const upcoming = contests
        .filter(c => {
            if (!c.start_time) return true;
            return new Date(c.start_time) > new Date(Date.now() - 3600000 * 2);
        })
        .slice(0, 5);

    if (statusEl) statusEl.textContent = `${upcoming.length} upcoming contest${upcoming.length !== 1 ? "s" : ""}`;

    listEl.innerHTML = upcoming.map(c => {
        const name = escapeHtml(c.name || "Contest");
        const start = formatContestTime(c.start_time);
        const end = formatContestTime(c.end_time);
        const badge = getContestBadge(c.start_time);
        const url = escapeHtml(c.url || "#");
        return `
            <div class="contest-item">
                <div class="contest-item-name">${name}</div>
                <div class="contest-item-time">Start: ${start}</div>
                <div class="contest-item-time">End: ${end}</div>
                <div class="contest-item-badges">${badge}</div>
                <a class="contest-item-link" href="${url}" target="_blank" rel="noopener noreferrer">View Contest →</a>
            </div>
        `;
    }).join("");
}

function applyToggleUI(platform, enabled) {
    const labelEl = document.getElementById(`toggleLabel-${platform}`);
    const toggleEl = document.getElementById(`toggle-${platform}`);
    if (labelEl) {
        labelEl.textContent = enabled ? "Notifications On" : "Notifications Off";
        labelEl.className = "contest-toggle-label" + (enabled ? " on" : "");
    }
    if (toggleEl) toggleEl.checked = enabled;
}

function checkAndNotify(platform, contests) {
    if (!contests || contests.length === 0) return;
    if (Notification.permission !== "granted") return;

    const soon = contests.find(c => {
        if (!c.start_time) return false;
        const diff = new Date(c.start_time) - Date.now();
        return diff > 0 && diff < 3600000 * 24;
    });

    if (soon) {
        new Notification(`🏆 ${platform} contest starting soon!`, {
            body: `${soon.name}\nStarts: ${formatContestTime(soon.start_time)}`,
            icon: "https://cp-logo.vercel.app/codeforces"
        });
    }
}

async function toggleContestNotification(platform, enabled) {
    applyToggleUI(platform, enabled);

    try {
        await fetch(
            `http://localhost:8089/api/contests/preferences?email=${encodeURIComponent(userEmail)}&platform=${encodeURIComponent(platform)}&enabled=${enabled}`,
            { method: "PUT" }
        );
    } catch (e) {
        console.error("Failed to save contest preference:", e);
    }

    if (enabled) {
        if (Notification.permission === "default") {
            const perm = await Notification.requestPermission();
            if (perm === "granted") {
                try {
                    const contests = await fetchPlatformContests(platform);
                    if (contests) checkAndNotify(platform, contests);
                } catch (e) { /* ignore */ }
            }
        } else if (Notification.permission === "granted") {
            try {
                const contests = await fetchPlatformContests(platform);
                if (contests) checkAndNotify(platform, contests);
            } catch (e) { /* ignore */ }
        }
    }
}

async function loadContests() {
    // Load saved preferences (5s timeout — backend is optional)
    let prefs = { CODECHEF: false, LEETCODE: false, CODEFORCES: false };
    try {
        const ctrl = new AbortController();
        const tid = setTimeout(() => ctrl.abort(), 5000);
        const res = await fetch(
            `http://localhost:8089/api/contests/preferences?email=${encodeURIComponent(userEmail)}`,
            { signal: ctrl.signal }
        );
        clearTimeout(tid);
        if (res.ok) prefs = await res.json();
    } catch (e) { /* backend offline — use defaults */ }

    for (const platform of CONTEST_PLATFORMS) {
        applyToggleUI(platform, prefs[platform] === true);
    }

    for (const platform of CONTEST_PLATFORMS) {
        const listEl  = document.getElementById(`contestList-${platform}`);
        const statusEl = document.getElementById(`contestStatus-${platform}`);
        try {
            const contests = await fetchPlatformContests(platform);
            if (contests && contests.length > 0) {
                renderContestList(platform, contests);
                if (prefs[platform] === true && Notification.permission === "granted") {
                    checkAndNotify(platform, contests);
                }
            } else {
                // Both sources failed — show a direct link so user can still check
                const platformName = platform.charAt(0) + platform.slice(1).toLowerCase();
                const link = PLATFORM_FALLBACK_URLS[platform];
                if (listEl) listEl.innerHTML = `
                    <div class="no-contests-msg">
                        Live data temporarily unavailable.<br>
                        <a href="${link}" target="_blank" rel="noopener noreferrer"
                           style="color:var(--primary-orange);font-weight:700;text-decoration:none;">
                            View contests on ${platformName} &rarr;
                        </a>
                    </div>`;
                if (statusEl) statusEl.textContent = "Visit platform";
            }
        } catch (e) {
            if (listEl) listEl.innerHTML = '<div class="no-contests-msg">Failed to load contests.</div>';
            if (statusEl) statusEl.textContent = "Error";
        }
    }
}

async function loadUser() {
    try {
        const response = await fetch(
            `http://localhost:8089/api/students/profile?email=${userEmail}`
        );

        const data = await response.json();

        if (data && data.name) {
            animateWelcome(data.name);
        } else {
            animateWelcome("User");
        }

        if (data && data.id != null) {
            localStorage.setItem("studentId", String(data.id));
        }

    } catch (error) {
        animateWelcome("User");
    }

    // Load social profiles after user loads
    loadSocialProfiles();
    loadProjects();
    loadContests();
}

loadUser();

window.addEventListener("pageshow", function () {
    resetResumeRecommendationsUI();
});

document.getElementById("analyticsModal").addEventListener("click", function (event) {
    if (event.target.id === "analyticsModal") {
        closeAnalytics();
    }
});

document.addEventListener("keydown", function (event) {
    if (event.key === "Escape") {
        closeAnalytics();
    }
});
