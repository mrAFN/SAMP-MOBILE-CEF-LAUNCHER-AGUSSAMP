//
// Created by shisisan on 21/08/2026.
// v2: sistem browser id (banyak UI sekaligus)
//
// Semua RPC yang menyasar browser membawa u16 browserId tepat setelah header (u16 packetId, u32 rpcId).
//

#include "SAMPMobileCef.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <sys/stat.h>
#include <time.h>
#include <jni.h>
#include "../Deps/RakNet/RakNet/RakClientInterface.h" // Must be included in CMAKE
#include "./Deps/RakNet/RakNet/BitStream.h" // Must be included in CMAKE

namespace cef
{
    static char g_szGamePath[512] = {0};
    static char g_szLogPath[512] = {0};
    static void* g_pRakClient = nullptr;
    static uint8_t g_nPacketId = 252;
    static bool g_bInitialized = false;

    static JavaVM*  g_pJavaVM = nullptr;
    static jclass    g_ClsCefClientManager = nullptr;

    static jmethodID g_MidOpenBrowser       = nullptr;
    static jmethodID g_MidCloseBrowser      = nullptr;
    static jmethodID g_MidShowBrowser       = nullptr;
    static jmethodID g_MidHideBrowser       = nullptr;
    static jmethodID g_MidSetUrl            = nullptr;
    static jmethodID g_MidSetFocus          = nullptr;
    static jmethodID g_MidDispatchServerEvt = nullptr;

    static SendPacketFn g_pfnSendPacket = nullptr;

    // ------------------------------------------------------------------ JNI helpers

    static JNIEnv* getJniEnv(bool* pDidAttach)
    {
        *pDidAttach = false;
        if (!g_pJavaVM) return nullptr;

        JNIEnv* env = nullptr;
        jint res = g_pJavaVM->GetEnv((void**)&env, JNI_VERSION_1_6);
        if (res == JNI_EDETACHED)
        {
            if (g_pJavaVM->AttachCurrentThread(&env, nullptr) != 0)
                return nullptr;
            *pDidAttach = true;
        }
        else if (res != JNI_OK)
        {
            return nullptr;
        }
        return env;
    }

    // Menjalankan fn(env) dengan JNIEnv yang valid. Exception Java selalu dibersihkan supaya
    // tidak menumpuk (pending exception + panggilan JNI berikutnya = abort).
    template <typename Fn>
    static void withJni(const char* what, jmethodID mid, Fn&& fn)
    {
        bool didAttach = false;
        JNIEnv* env = getJniEnv(&didAttach);
        if (!env) { log("%s: JavaVM not ready yet", what); return; }

        if (g_ClsCefClientManager && mid) {
            fn(env);
            if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        } else {
            log("%s: method not cached", what);
        }

        if (didAttach) g_pJavaVM->DetachCurrentThread();
    }

    static void callJavaOpenBrowser(uint16_t id, const char* szUrl, bool focused)
    {
        withJni("callJavaOpenBrowser", g_MidOpenBrowser, [&](JNIEnv* env) {
            jstring jUrl = env->NewStringUTF(szUrl);
            env->CallStaticVoidMethod(g_ClsCefClientManager, g_MidOpenBrowser, (jint) id, jUrl, (jboolean) focused);
            env->DeleteLocalRef(jUrl);
        });
    }

    static void callJavaCloseBrowser(uint16_t id)
    {
        withJni("callJavaCloseBrowser", g_MidCloseBrowser, [&](JNIEnv* env) {
            env->CallStaticVoidMethod(g_ClsCefClientManager, g_MidCloseBrowser, (jint) id);
        });
    }

    static void callJavaShowBrowser(uint16_t id)
    {
        withJni("callJavaShowBrowser", g_MidShowBrowser, [&](JNIEnv* env) {
            env->CallStaticVoidMethod(g_ClsCefClientManager, g_MidShowBrowser, (jint) id);
        });
    }

    static void callJavaHideBrowser(uint16_t id)
    {
        withJni("callJavaHideBrowser", g_MidHideBrowser, [&](JNIEnv* env) {
            env->CallStaticVoidMethod(g_ClsCefClientManager, g_MidHideBrowser, (jint) id);
        });
    }

    static void callJavaSetUrl(uint16_t id, const char* szUrl)
    {
        withJni("callJavaSetUrl", g_MidSetUrl, [&](JNIEnv* env) {
            jstring jUrl = env->NewStringUTF(szUrl);
            env->CallStaticVoidMethod(g_ClsCefClientManager, g_MidSetUrl, (jint) id, jUrl);
            env->DeleteLocalRef(jUrl);
        });
    }

    static void callJavaSetFocus(uint16_t id, bool isFocused)
    {
        withJni("callJavaSetFocus", g_MidSetFocus, [&](JNIEnv* env) {
            env->CallStaticVoidMethod(g_ClsCefClientManager, g_MidSetFocus, (jint) id, (jboolean) isFocused);
        });
    }

    static void callJavaDispatchServerEvent(uint16_t id, const char* szEventName, const char* szEventData)
    {
        withJni("callJavaDispatchServerEvent", g_MidDispatchServerEvt, [&](JNIEnv* env) {
            jstring jName = env->NewStringUTF(szEventName);
            jstring jData = env->NewStringUTF(szEventData);
            env->CallStaticVoidMethod(g_ClsCefClientManager, g_MidDispatchServerEvt, (jint) id, jName, jData);
            env->DeleteLocalRef(jName);
            env->DeleteLocalRef(jData);
        });
    }

    // ------------------------------------------------------------------ setup

    void setSendCallback(SendPacketFn fn)
    {
        g_pfnSendPacket = fn;
        log("cef::setSendCallback: registered (%p)", (void*)fn);
    }

    void applyJavaVM(JavaVM* vm)
    {
        g_pJavaVM = vm;
        log("cef::applyJavaVM: JavaVM registered (%p)", (void*)vm);

        JNIEnv* env = nullptr;
        if (vm->GetEnv((void**) &env, JNI_VERSION_1_6) != JNI_OK) {
            log("cef::applyJavaVM: failed to get JNIEnv on registering thread");
            return;
        }

        jclass localCls = env->FindClass("com/samp/cef/CefClientManager");
        if (!localCls) {
            log("cef::applyJavaVM: FindClass(CefClientManager) FAILED");
            env->ExceptionClear();
            return;
        }

        g_ClsCefClientManager = (jclass) env->NewGlobalRef(localCls);
        env->DeleteLocalRef(localCls);

        auto getStatic = [&](const char* name, const char* sig) -> jmethodID {
            jmethodID m = env->GetStaticMethodID(g_ClsCefClientManager, name, sig);
            if (!m || env->ExceptionCheck()) {
                env->ExceptionClear();
                log("cef::applyJavaVM: GetStaticMethodID(%s %s) FAILED", name, sig);
                return nullptr;
            }
            return m;
        };

        g_MidOpenBrowser       = getStatic("staticOpenBrowser",        "(ILjava/lang/String;Z)V");
        g_MidCloseBrowser      = getStatic("staticCloseBrowser",       "(I)V");
        g_MidShowBrowser       = getStatic("staticShowBrowser",        "(I)V");
        g_MidHideBrowser       = getStatic("staticHideBrowser",        "(I)V");
        g_MidSetUrl            = getStatic("staticSetBrowserUrl",      "(ILjava/lang/String;)V");
        g_MidSetFocus          = getStatic("staticSetBrowserFocus",    "(IZ)V");
        g_MidDispatchServerEvt = getStatic("staticDispatchServerEvent","(ILjava/lang/String;Ljava/lang/String;)V");

        log("cef::applyJavaVM: class+methods cached successfully");
    }

    void setGamePath(const char* szPath)
    {
        if (!szPath) return;

        snprintf(g_szGamePath, sizeof(g_szGamePath), "%s", szPath);
        snprintf(g_szLogPath, sizeof(g_szLogPath), "%s/SAMP/cef.log", szPath);

        char szDir[512];
        snprintf(szDir, sizeof(szDir), "%s/SAMP", szPath);
        mkdir(szDir, 0755);

        log("CEF Client initialized. Storage path: %s", szPath);
    }

    void initNetwork(void* pRakClient, uint8_t packetId)
    {
        g_pRakClient = pRakClient;
        g_nPacketId = packetId;
        g_bInitialized = true;

        log("CEF Network initialized. RakClient: %p, Packet ID: %d", pRakClient, packetId);
    }

    bool isInitialized()
    {
        return g_bInitialized;
    }

    void handleServerConnection()
    {
        if (!g_bInitialized) return;

        log("Sending LibraryInit signal to SA:MP Server...");

        uint8_t buffer[6];
        uint16_t packetId16 = (uint16_t) g_nPacketId;
        memcpy(buffer, &packetId16, 2);
        uint32_t rpcId = RPC_LibraryInit;
        memcpy(buffer + 2, &rpcId, 4);

        bool ok = g_pfnSendPacket && g_pfnSendPacket(buffer, sizeof(buffer), true, true);

        if (ok) log("RPC_LibraryInit handshake sent successfully.");
        else    log("RPC_LibraryInit handshake FAILED to send.");
    }

    // ------------------------------------------------------------------ client -> server

    // u16 packetId | u32 rpcId | u16 browserId | u16 nameLen | name | u16 dataLen | data
    void sendClientEvent(uint16_t browserId, const char* szEventName, const char* szEventData)
    {
        if (!g_bInitialized || !szEventName || !szEventData) return;

        size_t nameLenFull = strlen(szEventName);
        size_t dataLenFull = strlen(szEventData);
        if (nameLenFull > 0xFFFF || dataLenFull > 0xFFFF) {
            log("ClientEvent dropped: payload too large (name=%zu, data=%zu)", nameLenFull, dataLenFull);
            return;
        }

        uint16_t nameLen = (uint16_t) nameLenFull;
        uint16_t dataLen = (uint16_t) dataLenFull;

        size_t totalSize = 2 + 4 + 2 + 2 + nameLen + 2 + dataLen;
        uint8_t* pBuffer = (uint8_t*) malloc(totalSize);
        if (!pBuffer) return;

        size_t offset = 0;
        uint16_t packetId16 = (uint16_t) g_nPacketId;
        memcpy(pBuffer + offset, &packetId16, 2); offset += 2;

        uint32_t rpcId = RPC_ClientEvent;
        memcpy(pBuffer + offset, &rpcId, 4); offset += 4;

        memcpy(pBuffer + offset, &browserId, 2); offset += 2;

        memcpy(pBuffer + offset, &nameLen, 2); offset += 2;
        memcpy(pBuffer + offset, szEventName, nameLen); offset += nameLen;

        memcpy(pBuffer + offset, &dataLen, 2); offset += 2;
        memcpy(pBuffer + offset, szEventData, dataLen); offset += dataLen;

        bool ok = g_pfnSendPacket && g_pfnSendPacket(pBuffer, totalSize, true, true);

        if (ok) log("ClientEvent sent -> browser: %u, event: '%s', dataLen: %u", browserId, szEventName, dataLen);
        else    log("ClientEvent FAILED to send -> browser: %u, event: '%s'", browserId, szEventName);

        free(pBuffer);
    }

    // u16 packetId | u32 rpcId | u16 browserId | u8 success | i16 errorCode
    void sendBrowserInit(uint16_t browserId, bool isSuccess, int16_t errorCode)
    {
        if (!g_bInitialized) return;

        uint8_t buffer[11];
        size_t offset = 0;

        uint16_t packetId16 = (uint16_t) g_nPacketId;
        memcpy(buffer + offset, &packetId16, 2); offset += 2;

        uint32_t rpcId = RPC_BrowserInit;
        memcpy(buffer + offset, &rpcId, 4); offset += 4;

        memcpy(buffer + offset, &browserId, 2); offset += 2;

        uint8_t boolVal = isSuccess ? 1 : 0;
        memcpy(buffer + offset, &boolVal, 1); offset += 1;

        memcpy(buffer + offset, &errorCode, 2); offset += 2;

        bool ok = g_pfnSendPacket && g_pfnSendPacket(buffer, sizeof(buffer), true, true);

        if (ok) log("RPC_BrowserInit sent -> browser=%u, success=%d, errorCode=%d", browserId, (int) isSuccess, (int) errorCode);
        else    log("RPC_BrowserInit FAILED to send (browser=%u).", browserId);
    }

    // ------------------------------------------------------------------ server -> client

    // Pembaca paket dengan pengecekan batas. Semua angka little-endian.
    struct Reader
    {
        const uint8_t* p;
        size_t len;
        size_t off;

        bool u8(uint8_t& v)   { if (off + 1 > len) return false; v = p[off]; off += 1; return true; }
        bool u16(uint16_t& v) { if (off + 2 > len) return false; memcpy(&v, p + off, 2); off += 2; return true; }

        // baca string sepanjang n byte ke out (cap termasuk '\0'); false kalau tidak muat / paket terpotong
        bool str(char* out, size_t cap, uint16_t n)
        {
            if ((size_t) n >= cap) return false;
            if (off + n > len) return false;
            memcpy(out, p + off, n);
            out[n] = '\0';
            off += n;
            return true;
        }
    };

    // all RPC packets share the same header: u16 packetId, u32 rpcId.
    // browser RPCs continue with u16 browserId.
    void handlePacket(void* pPacket)
    {
        if (!pPacket || !g_bInitialized) return;

        Packet* pkt = (Packet*) pPacket;
        if (!pkt->data || pkt->length < 6) return;

        const uint8_t* pData = pkt->data;

        uint16_t packetId = 0;
        memcpy(&packetId, pData, sizeof(uint16_t));
        if (packetId != g_nPacketId) return;

        uint32_t rpcId = 0;
        memcpy(&rpcId, pData + 2, sizeof(uint32_t));

        Reader r = { pData, (size_t) pkt->length, 6 };
        uint16_t browserId = 0;

        switch (rpcId)
        {
            case RPC_InitBrowser:
            {
                uint8_t focused = 0;
                uint16_t urlLen = 0;
                char szUrl[1024];
                if (!r.u16(browserId) || !r.u8(focused) || !r.u16(urlLen) || !r.str(szUrl, sizeof(szUrl), urlLen)) {
                    log("RPC_InitBrowser: malformed packet");
                    break;
                }
                log("RPC_InitBrowser -> browser=%u focused=%d URL: %s", browserId, (int) focused, szUrl);
                callJavaOpenBrowser(browserId, szUrl, focused != 0);
                break;
            }
            case RPC_DestroyBrowser:
                if (!r.u16(browserId)) { log("RPC_DestroyBrowser: malformed packet"); break; }
                log("RPC_DestroyBrowser -> browser=%u", browserId);
                callJavaCloseBrowser(browserId);
                break;
            case RPC_ShowBrowser:
                if (!r.u16(browserId)) { log("RPC_ShowBrowser: malformed packet"); break; }
                log("RPC_ShowBrowser -> browser=%u", browserId);
                callJavaShowBrowser(browserId);
                break;
            case RPC_HideBrowser:
                if (!r.u16(browserId)) { log("RPC_HideBrowser: malformed packet"); break; }
                log("RPC_HideBrowser -> browser=%u", browserId);
                callJavaHideBrowser(browserId);
                break;
            case RPC_SetBrowserUrl:
            {
                uint16_t urlLen = 0;
                char szUrl[1024];
                if (!r.u16(browserId) || !r.u16(urlLen) || !r.str(szUrl, sizeof(szUrl), urlLen)) {
                    log("RPC_SetBrowserUrl: malformed packet");
                    break;
                }
                log("RPC_SetBrowserUrl -> browser=%u URL: %s", browserId, szUrl);
                callJavaSetUrl(browserId, szUrl);
                break;
            }
            case RPC_ChangeBrowserFocus:
            {
                uint8_t isFocused = 0;
                if (!r.u16(browserId) || !r.u8(isFocused)) { log("RPC_ChangeBrowserFocus: malformed packet"); break; }
                log("RPC_ChangeBrowserFocus -> browser=%u focused=%d", browserId, (int) isFocused);
                callJavaSetFocus(browserId, isFocused != 0);
                break;
            }
            case RPC_ServerEvent:
            {
                uint16_t nameLen = 0, dataLen = 0;
                char szEventName[128];

                if (!r.u16(browserId) || !r.u16(nameLen) || !r.str(szEventName, sizeof(szEventName), nameLen) || !r.u16(dataLen)) {
                    log("RPC_ServerEvent: malformed packet (header/name)");
                    break;
                }
                if (r.off + dataLen > r.len) { log("RPC_ServerEvent: packet too short (data)"); break; }

                char* szEventData = (char*) malloc((size_t) dataLen + 1);
                if (!szEventData) break;
                memcpy(szEventData, pData + r.off, dataLen);
                szEventData[dataLen] = '\0';

                log("RPC_ServerEvent -> browser=%u name: %s, dataLen: %u", browserId, szEventName, dataLen);
                callJavaDispatchServerEvent(browserId, szEventName, szEventData);

                free(szEventData);
                break;
            }
            default:
                log("Unknown CEF RPC ID: %u", rpcId);
                break;
        }
    }

    void log(const char* fmt, ...)
    {
        if (g_szLogPath[0] == '\0') return;

        FILE* f = fopen(g_szLogPath, "a");
        if (!f) return;

        time_t rawtime;
        struct tm* timeinfo;
        char timebuf[32];

        time(&rawtime);
        timeinfo = localtime(&rawtime);
        strftime(timebuf, sizeof(timebuf), "[%Y-%m-%d %H:%M:%S]", timeinfo);

        fprintf(f, "%s ", timebuf);

        va_list args;
        va_start(args, fmt);
        vfprintf(f, fmt, args);
        va_end(args);

        fprintf(f, "\n");
        fclose(f);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_samp_cef_CefClientManager_nativeSendClientEvent(JNIEnv* env, jobject, jint browserId, jstring eventName, jstring eventData)
{
    if (!eventName || !eventData) return;
    if (browserId < 0 || browserId > 0xFFFE) return;

    const char* szEventName = env->GetStringUTFChars(eventName, nullptr);
    const char* szEventData = env->GetStringUTFChars(eventData, nullptr);

    if (szEventName && szEventData) {
        cef::sendClientEvent((uint16_t) browserId, szEventName, szEventData);
    }

    if (szEventName) env->ReleaseStringUTFChars(eventName, szEventName);
    if (szEventData) env->ReleaseStringUTFChars(eventData, szEventData);
}

extern "C" JNIEXPORT void JNICALL
Java_com_samp_cef_CefClientManager_nativeOnBrowserInit(JNIEnv* env, jobject, jint browserId, jboolean isSuccess, jint errorCode)
{
    if (browserId < 0 || browserId > 0xFFFE) return;

    cef::log("nativeOnBrowserInit: browser=%d success=%d, errorCode=%d", (int) browserId, (int) isSuccess, (int) errorCode);
    cef::sendBrowserInit((uint16_t) browserId, (bool) isSuccess, (int16_t) errorCode);
}