package woyou.aidlservice.jiuiv5;
import woyou.aidlservice.jiuiv5.ICallback;
interface IWoyouService {
    void printerInit(in ICallback callback);
    void printTextWithFont(String text, String typeface, float fontsize, in ICallback callback);
    void setAlignment(int alignment, in ICallback callback);
    void lineWrap(int n, in ICallback callback);
    void cutPaper(in ICallback callback);
}
