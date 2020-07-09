
/*
   DO NOT EDIT THIS FILE!

   This file is automatically generated by tools/autogen.py from tools/public_api.h.
   Run this to regenerate:
       make autogen

*/

struct _HPyContext_s {
    int ctx_version;
    HPy h_None;
    HPy h_True;
    HPy h_False;
    HPy h_ValueError;
    HPy h_TypeError;
    void* (*ctx_Module_Create)(HPyContext ctx, HPyModuleDef *def);
    void* (*ctx_Dup)(HPyContext ctx, void* h);
    void (*ctx_Close)(HPyContext ctx, void* h);
    void* (*ctx_Long_FromLong)(HPyContext ctx, long value);
    void* (*ctx_Long_FromLongLong)(HPyContext ctx, long long v);
    void* (*ctx_Long_FromUnsignedLongLong)(HPyContext ctx, unsigned long long v);
    long (*ctx_Long_AsLong)(HPyContext ctx, void* h);
    void* (*ctx_Float_FromDouble)(HPyContext ctx, double v);
    void* (*ctx_Number_Add)(HPyContext ctx, void* h1, void* h2);
    void (*ctx_Err_SetString)(HPyContext ctx, void* h_type, const char *message);
    int (*ctx_Err_Occurred)(HPyContext ctx);
    int (*ctx_Object_IsTrue)(HPyContext ctx, void* h);
    void* (*ctx_GetAttr)(HPyContext ctx, void* obj, void* name);
    void* (*ctx_GetAttr_s)(HPyContext ctx, void* obj, const char *name);
    int (*ctx_HasAttr)(HPyContext ctx, void* obj, void* name);
    int (*ctx_HasAttr_s)(HPyContext ctx, void* obj, const char *name);
    int (*ctx_SetAttr)(HPyContext ctx, void* obj, void* name, void* value);
    int (*ctx_SetAttr_s)(HPyContext ctx, void* obj, const char *name, void* value);
    void* (*ctx_GetItem)(HPyContext ctx, void* obj, void* key);
    void* (*ctx_GetItem_i)(HPyContext ctx, void* obj, HPy_ssize_t idx);
    void* (*ctx_GetItem_s)(HPyContext ctx, void* obj, const char *key);
    int (*ctx_SetItem)(HPyContext ctx, void* obj, void* key, void* value);
    int (*ctx_SetItem_i)(HPyContext ctx, void* obj, HPy_ssize_t idx, void* value);
    int (*ctx_SetItem_s)(HPyContext ctx, void* obj, const char *key, void* value);
    int (*ctx_Bytes_Check)(HPyContext ctx, void* h);
    HPy_ssize_t (*ctx_Bytes_Size)(HPyContext ctx, void* h);
    HPy_ssize_t (*ctx_Bytes_GET_SIZE)(HPyContext ctx, void* h);
    char *(*ctx_Bytes_AsString)(HPyContext ctx, void* h);
    char *(*ctx_Bytes_AS_STRING)(HPyContext ctx, void* h);
    void* (*ctx_Unicode_FromString)(HPyContext ctx, const char *utf8);
    int (*ctx_Unicode_Check)(HPyContext ctx, void* h);
    void* (*ctx_Unicode_AsUTF8String)(HPyContext ctx, void* h);
    void* (*ctx_Unicode_FromWideChar)(HPyContext ctx, const wchar_t *w, HPy_ssize_t size);
    void* (*ctx_List_New)(HPyContext ctx, HPy_ssize_t len);
    int (*ctx_List_Append)(HPyContext ctx, void* h_list, void* h_item);
    void* (*ctx_Dict_New)(HPyContext ctx);
    int (*ctx_Dict_SetItem)(HPyContext ctx, void* h_dict, void* h_key, void* h_val);
    void* (*ctx_Dict_GetItem)(HPyContext ctx, void* h_dict, void* h_key);
    void* (*ctx_FromPyObject)(HPyContext ctx, struct _object *obj);
    struct _object *(*ctx_AsPyObject)(HPyContext ctx, void* h);
    struct _object *(*ctx_CallRealFunctionFromTrampoline)(HPyContext ctx, struct _object *self, struct _object *args, struct _object *kw, void *func, int ml_flags);
};
