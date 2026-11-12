#include "Game.h"
#include "../Client.h"

sa::CRunningScript* gst;
char ScriptBuf[0xff];
uintptr_t* pdwParamVars[MAX_SCRIPT_VARS];
bool bIsFloatParam[MAX_SCRIPT_VARS];
float* pFloatParam[MAX_SCRIPT_VARS];

uint8_t ExecuteScriptBuf()
{
	gst->m_pPCPointer = (uint8_t*) ScriptBuf;
	gst->ProcessOneCommand();
	return gst->m_bCmpFlag;
}

void InitScripting()
{
	spdlog::info("Initiating scripting...");
	gst = new sa::CRunningScript{};
}

std::string dec2hex(int dec)
{
	std::stringstream ss;
	ss << std::hex << std::uppercase << std::setfill('0') << std::setw(4) << dec;
	return ss.str();
}

std::string g_lastScriptCommand;

int ScriptCommand(const SCRIPT_COMMAND* pScriptCommand, ...)
{
//	spdlog::info("ScriptCommand: 0x{}", dec2hex(pScriptCommand->OpCode));

	g_lastScriptCommand = "0x" + dec2hex(pScriptCommand->OpCode);

	va_list ap;
	const char* p = pScriptCommand->Params;
	va_start(ap, pScriptCommand);
	memcpy(&ScriptBuf, &pScriptCommand->OpCode, 2);
	int buf_pos = 2;
	uint16_t var_pos = 0;

	for (int i = 0; i < MAX_SCRIPT_VARS; i++) {
		gst->m_nLocals[i] = 0;
		bIsFloatParam[i] = false;
		pdwParamVars[i] = nullptr;
		pFloatParam[i] = nullptr;
	}

	while (*p) {
		switch (*p) {
			case 'i': {
				int i = va_arg(ap, int);
				ScriptBuf[buf_pos] = 0x01;
				buf_pos++;
				memcpy(&ScriptBuf[buf_pos], &i, 4);
				buf_pos += 4;
				break;
			}
			case 'f': {
				float f = (float) va_arg(ap, double);
				ScriptBuf[buf_pos] = 0x06;
				buf_pos++;
				memcpy(&ScriptBuf[buf_pos], &f, 4);
				buf_pos += 4;
				break;
			}
			case 'v': {
				uint32_t* v = va_arg(ap, uint32_t*);
				ScriptBuf[buf_pos] = 0x03;
				buf_pos++;
				pdwParamVars[var_pos] = reinterpret_cast<uintptr_t*>(v);
				gst->m_nLocals[var_pos] = v ? *v : 0;
				bIsFloatParam[var_pos] = false;
				memcpy(&ScriptBuf[buf_pos], &var_pos, 2);
				buf_pos += 2;
				var_pos++;
				break;
			}
			case 'F': {
				float* f_ptr = va_arg(ap, float*);
				ScriptBuf[buf_pos] = 0x03;
				buf_pos++;
				pFloatParam[var_pos] = f_ptr;
				float initial_val = f_ptr ? *f_ptr : 0.0f;
				uint32_t raw_val;
				memcpy(&raw_val, &initial_val, 4);
				gst->m_nLocals[var_pos] = raw_val;
				bIsFloatParam[var_pos] = true;
				memcpy(&ScriptBuf[buf_pos], &var_pos, 2);
				buf_pos += 2;
				var_pos++;
				break;
			}
			case 's': {
				char* sz = va_arg(ap, char*);
				unsigned char aLen = strlen(sz);
				ScriptBuf[buf_pos] = 0x0E;
				buf_pos++;
				ScriptBuf[buf_pos] = aLen;
				buf_pos++;
				memcpy(&ScriptBuf[buf_pos], sz, aLen);
				buf_pos += aLen;
				break;
			}
			case 'z': {
				ScriptBuf[buf_pos] = 0x00;
				buf_pos++;
				break;
			}
			default: {
				return 0;
			}
		}
		++p;
	}
	va_end(ap);

	int result = ExecuteScriptBuf();
	if (var_pos) {
		for (int i = 0; i < var_pos; i++) {
			if (bIsFloatParam[i]) {
				float fVal;
				uint32_t rawVal = (uint32_t) gst->m_nLocals[i];
				memcpy(&fVal, &rawVal, 4);
				if (pFloatParam[i]) *pFloatParam[i] = fVal;
			} else {
				if (pdwParamVars[i]) *pdwParamVars[i] = gst->m_nLocals[i];
			}
		}
	}

	return result;
}
