const cache = localStorage.getItem("modVersionCache")
const CACHE_TTL = 1000 * 60;
const version = $("#mod-version");
const donateButton = $("#donate-button")
const downloadButton = $("#download-button")
const disclaimer = $("#disclaimer")
const menu = $(".hb-menu");
const btn = $("#hb-logo");
let hamburguerMenuOpen = false

$(() => {
    version.addClass("loading");
    const data = JSON.parse(cache)
    if (data && Date.now() - data.timestamp < CACHE_TTL) {
        updateModVersion(data.version, data.minecraftVersion)
    } else {
        fetch("https://api.modrinth.com/v2/project/vulkanmod/version")
            .then((res) => res.json())
            .then((data) => {
                const latest = data[0];
                const [major, minor, patch] = latest.version_number.split(".").map(Number);
                const [mMajor, mMinor, mPatch] = latest.game_versions[0].split(".").map(Number)

                const obj = {
                    version: `${major}.${minor}.${patch}`,
                    minecraftVersion: `${mMajor}.${mMinor}.${mPatch}`,
                    timestamp: Date.now()
                }

                localStorage.setItem("modVersionCache", JSON.stringify(obj))

                updateModVersion(obj.version, obj.minecraftVersion)
            })
            .catch(() => {
                version.text("Unable to fetch modrinth API!");
                version.removeClass("loading").addClass("done");
            });
    }

    new Modal(`
        <h1>Disclaimer:</h1>
        <ul>
            <li>Vulkan and the Vulkan logo are registered trademarks of the Khronos Group Inc.</li>
            <li>OpenGL and the OpenGL logo are registered trademarks of the Khronos Group Inc.</li>
            <li>Minecraft is the trademark of Microsoft Corporation.</li>
            <li>Fabric is the trademark of FabricMC.</li>
        </ul>
        <div class="button-container">
            <button id="close-modal" class="pretty">Close</button>
        </div>
    `, disclaimer)

    new Modal(`
        <h1>Contributors:</h1>
        <ul>
            <li><a href="https://github.com/thr3343">thr3343</a> - Core Mod Development</li>
            <li><a href="https://github.com/CADIndie">CADIndie</a> - Forge Port and many other fixes</li>
            <li><a href="https://pieman.dev/">piemanau</a> - Website adjustments</li>
            <li><a href="https://github.com/Sollace">Sollace</a> - OpenGL Mod Compatibility fixes</li>
            <li><a href="https://www.youtube.com/@SpidFightFR">SpidFightFR</a> - VulkanMod contents</li>
            <li><a href="https://github.com/khanhduytran0">DuyKhanhTran</a> - Mobile Devices ports</li>
            <li><a href="https://github.com/hechfx">hechfx</a> - Website redesign </li>
        </ul>
        <div class="button-container">
            <button id="close-modal" class="pretty">Close</button>
        </div>
    `, $("#contributors"), $("#contributors-f"))

    disclaimer.text(disclaimer.text().replace("{year}", new Date().getFullYear()))

    if (isMobile()) {
        $("#hb-logo").html(`<i class="bi bi-list"></i>`)

        btn.on("click", () => {
            if (hamburguerMenuOpen) {
                menu.removeClass("open");
                $("body").css("overflow", "visible")
                setTimeout(() => {
                    menu.css("display", "none");
                }, 300);
                hamburguerMenuOpen = false;
            } else {
                $("body").css("overflow", "hidden")
                requestAnimationFrame(() => {
                    menu.addClass("open");
                });
                menu.css("display", "flex");

                hamburguerMenuOpen = true;
            }
        });
    }
})

function updateModVersion(ver, minecraftVersion) {
    const currentVersion = { x: 0, y: 0, z: 0 };
    const currentMinecraftVersion = { x: 0, y: 0, z: 0 }

    const [x, y, z] = ver.split(".").map(Number);
    const [mX, mY, mZ] = minecraftVersion.split(".").map(Number)

    const targetVersion = { x, y, z };
    const targetMinecraftVersion = { mX, mY, mZ }
    const duration = 800;
    const start = performance.now();

    function animate(time) {
        const progress = Math.min((time - start) / duration, 1);
        const ease = 1 - Math.pow(1 - progress, 2);

        currentVersion.x = Math.floor(targetVersion.x * ease);
        currentVersion.y = Math.floor(targetVersion.y * ease);
        currentVersion.z = Math.floor(targetVersion.z * ease);

        currentMinecraftVersion.x = Math.floor(targetMinecraftVersion.mX * ease);
        currentMinecraftVersion.y = Math.floor(targetMinecraftVersion.mY * ease);
        currentMinecraftVersion.z = Math.floor(targetMinecraftVersion.mZ * ease);

        version.text(`${currentVersion.x}.${currentVersion.y}.${currentVersion.z} ~ ${currentMinecraftVersion.x}.${currentMinecraftVersion.y}.${currentMinecraftVersion.z}`);

        if (progress < 1) {
            requestAnimationFrame(animate);
        } else {
            version.text(`${x}.${y}.${z} ~ ${currentMinecraftVersion.x}.${currentMinecraftVersion.y}.${currentMinecraftVersion.z}`);
            version.removeClass("loading").addClass("done");
        }
    }

    requestAnimationFrame(animate);
}

function isMobile() {
    return window.innerWidth <= 768;
}