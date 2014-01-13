package pontezit.android.tilos.com.transport;

import pontezit.android.tilos.com.bean.UriBean;

public class MMST extends MMS {

	private static final String PROTOCOL = "mmst";
	
	public MMST() {
		super();
	}
	
	public MMST(UriBean uri) {
		super(uri);
	}
	
	public static String getProtocolName() {
		return PROTOCOL;
	}	
	
	protected String getPrivateProtocolName() {
		return PROTOCOL;
	}
}
