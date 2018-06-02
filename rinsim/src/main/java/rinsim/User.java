package rinsim;

import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.ParcelDTO;

public class User extends Parcel{
	
	private Station destination;
	private long deadline;

	public User(ParcelDTO parcelDto, long deadline, Station dest) {
		super(parcelDto);
		setDeadline(deadline);
		if(dest == null)
			System.out.println(dest);
		setDestination(dest);
	}
	
	public Station getDestination() {
		return destination;
	}

	public void setDestination(Station destination) {
		this.destination = destination;
	}

	public long getDeadline() {
		return deadline;
	}

	public void setDeadline(long deadline) {
		this.deadline = deadline;
	}

}
