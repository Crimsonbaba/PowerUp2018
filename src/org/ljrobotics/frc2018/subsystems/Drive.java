package org.ljrobotics.frc2018.subsystems;

import org.ljrobotics.frc2018.Constants;
import org.ljrobotics.frc2018.commands.JoystickDrive;
import org.ljrobotics.frc2018.loops.Loop;
import org.ljrobotics.frc2018.loops.Looper;
import org.ljrobotics.frc2018.state.Kinematics;
import org.ljrobotics.frc2018.state.RobotState;
import org.ljrobotics.lib.util.DriveSignal;
import org.ljrobotics.lib.util.control.Lookahead;
import org.ljrobotics.lib.util.control.Path;
import org.ljrobotics.lib.util.control.PathFollower;
import org.ljrobotics.lib.util.drivers.CANTalonFactory;
import org.ljrobotics.lib.util.drivers.LazyCANTalon;
import org.ljrobotics.lib.util.drivers.LazyGyroscope;
import org.ljrobotics.lib.util.math.RigidTransform2d;
import org.ljrobotics.lib.util.math.Rotation2d;
import org.ljrobotics.lib.util.math.Twist2d;

import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.NeutralMode;
import com.ctre.phoenix.motorcontrol.StatusFrame;
import com.ctre.phoenix.motorcontrol.can.TalonSRX;

import edu.wpi.first.wpilibj.command.Subsystem;
import edu.wpi.first.wpilibj.interfaces.Gyro;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

/**
 * The Drive subsystem. This subsystem is responsible for everything regarding
 * driving. It controls the four drive motors.
 *
 * @author Max
 *
 */
public class Drive extends Subsystem implements LoopingSubsystem {

	private static Drive instance;

	public static Drive getInstance() {
		if (instance == null) {
			TalonSRX frontLeft = new LazyCANTalon(Constants.FRONT_LEFT_MOTOR_ID);
			TalonSRX frontRight = new LazyCANTalon(Constants.FRONT_RIGHT_MOTOR_ID);
			TalonSRX rearLeft = new LazyCANTalon(Constants.REAR_LEFT_MOTOR_ID);
			TalonSRX rearRight = new LazyCANTalon(Constants.REAR_RIGHT_MOTOR_ID);

			RobotState robotState = RobotState.getInstance();
			LazyGyroscope gyro = LazyGyroscope.getInstance();

			instance = new Drive(frontLeft, frontRight, rearLeft, rearRight, robotState, gyro);
		}
		return instance;
	}

	// The robot drivetrain's various states.
	public enum DriveControlState {
		VELOCITY_SETPOINT, // Under PID velocity control
		PATH_FOLLOWING, // Following a path
		OPEN_LOOP // Used to drive control
	}

	public static final int VELOCITY_CONTROL_SLOT = 0;
	//Sensors
	private Gyro gyro;

	// The drive loop definition
	private class DriveLoop implements Loop {

		public void onStart( double timestamp ) {

		}

		public void onLoop( double timestamp ) {
			switch( driveControlState  ) {
			case VELOCITY_SETPOINT:
				//TODO add a way to get the left and right inches per second
				//updateVelocitySetpoint(left_inches_per_sec, right_inches_per_sec);
				break;
			case PATH_FOLLOWING:
				//TODO add a write to CVS file function
				updatePathFollower( timestamp );
				break;
			default:

			}
		}
		
		public void onStop( double timestamp ) {

		}

	}
	// The local drive loop
	private DriveLoop driveLoop = new DriveLoop();

	// Talons
	private TalonSRX leftMaster;
	private TalonSRX rightMaster;
	private TalonSRX leftSlave;
	private TalonSRX rightSlave;

	// Control States
	private DriveControlState driveControlState;

	// Controllers
	private PathFollower pathFollower;
	private RobotState robotState;

	// Hardware States
	private NeutralMode isBrakeMode;

	private Path currentPath;

	/**
	 * Creates a new Drive Subsystem from that controls the given motor controllers.
	 *
	 * @param frontLeft
	 *            the font left talon motor controller
	 * @param frontRight
	 *            the font right talon motor controller
	 * @param backLeft
	 *            the back left talon motor controller
	 * @param backRight
	 *            the back right talon motor controller
	 */
	public Drive(TalonSRX frontLeft, TalonSRX frontRight, TalonSRX backLeft, TalonSRX backRight, RobotState robotState,
			Gyro gyro) {

		this.robotState = robotState;
		this.gyro = gyro;

		this.leftMaster = frontLeft;
		this.rightMaster = frontRight;
		this.leftSlave = backLeft;
		this.rightSlave = backRight;

		CANTalonFactory.updateCANTalonToDefault(this.leftMaster);

		CANTalonFactory.updatePermanentSlaveTalon(this.leftSlave, this.leftMaster.getDeviceID());
		leftMaster.getStatusFramePeriod(StatusFrame.Status_2_Feedback0, 5);

		CANTalonFactory.updateCANTalonToDefault(this.rightMaster);

		CANTalonFactory.updatePermanentSlaveTalon(this.rightSlave, this.rightMaster.getDeviceID());
		leftMaster.getStatusFramePeriod(StatusFrame.Status_2_Feedback0, 5);

		this.driveControlState = DriveControlState.OPEN_LOOP;

		this.isBrakeMode = NeutralMode.Coast;
		this.setNeutralMode(NeutralMode.Brake);
	}

	@Override
	public void stop() {
		this.leftMaster.set(ControlMode.PercentOutput, 0);
		this.rightMaster.set(ControlMode.PercentOutput, 0);
	}

	@Override
	public void reset() {

	}

	@Override
	public void registerEnabledLoops(Looper enabledLooper) {
		enabledLooper.register(driveLoop);
	}

	public void setOpenLoop(DriveSignal driveSignal) {
		if (this.driveControlState != DriveControlState.OPEN_LOOP) {
            this.leftMaster.configNominalOutputForward(0, 0);
            this.rightMaster.configNominalOutputForward(0, 0);

            this.leftMaster.configNominalOutputReverse(0, 0);
            this.rightMaster.configNominalOutputReverse(0, 0);
            this.driveControlState = DriveControlState.OPEN_LOOP;
            setNeutralMode(NeutralMode.Coast);
        }
		double left = driveSignal.getLeft();
		double right = driveSignal.getRight();
		left = Math.min(Math.max(left, -1), 1);
		right = Math.min(Math.max(right, -1), 1);
		this.leftMaster.set(ControlMode.PercentOutput, left);
		this.rightMaster.set(ControlMode.PercentOutput, right);
	}

	/**
	 * Start up velocity mode. This sets the drive train in high gear as well.
	 *
	 * @param left_inches_per_sec
	 * @param right_inches_per_sec
	 */
	public synchronized void setVelocitySetpoint(double left_inches_per_sec, double right_inches_per_sec) {
		configureTalonsForSpeedControl();
		driveControlState = DriveControlState.VELOCITY_SETPOINT;
		updateVelocitySetpoint(left_inches_per_sec, right_inches_per_sec);
	}

	/**
	 * Called periodically when the robot is in path following mode. Updates the
	 * path follower with the robots latest pose, distance driven, and velocity, the
	 * updates the wheel velocity setpoints.
	 */
	public void updatePathFollower(double timestamp) {
		RigidTransform2d robot_pose = robotState.getLatestFieldToVehicle().getValue();
		Twist2d command = pathFollower.update(timestamp, robot_pose, robotState.getDistanceDriven(),
				robotState.getPredictedVelocity().dx);
		if (!pathFollower.isFinished()) {
			Kinematics.DriveVelocity setpoint = Kinematics.inverseKinematics(command);
			updateVelocitySetpoint(setpoint.left, setpoint.right);
		} else {
			updateVelocitySetpoint(0, 0);
		}
	}

	/**
	 * Adjust Velocity setpoint (if already in velocity mode)
	 *
	 * @param left_inches_per_sec
	 * @param right_inches_per_sec
	 */
	private synchronized void updateVelocitySetpoint(double left_inches_per_sec, double right_inches_per_sec) {
		if (usesTalonVelocityControl(driveControlState)) {
			final double max_desired = Math.max(Math.abs(left_inches_per_sec), Math.abs(right_inches_per_sec));
			final double scale = max_desired > Constants.DRIVE_MAX_SETPOINT ? Constants.DRIVE_MAX_SETPOINT / max_desired
					: 1.0;
			leftMaster.set(ControlMode.Velocity, inchesPerSecondToRpm(left_inches_per_sec * scale));
			rightMaster.set(ControlMode.Velocity, inchesPerSecondToRpm(right_inches_per_sec * scale));
		} else {
			System.out.println("Hit a bad velocity control state");
			leftMaster.set(ControlMode.Velocity, 0);
			rightMaster.set(ControlMode.Velocity, 0);
		}
	}

	public synchronized boolean isDoneWithPath() {
		if (driveControlState == DriveControlState.PATH_FOLLOWING && pathFollower != null) {
			return pathFollower.isFinished();
		} else {
			System.out.println("Robot is not in path following mode");
			return true;
		}
	}

	private static double inchesToRotations(double inches) {
		return inches / (Constants.DRIVE_WHEEL_DIAMETER_INCHES * Math.PI);
	}

	private static double inchesPerSecondToRpm(double inches_per_second) {
		return inchesToRotations(inches_per_second) * 60;
	}

	/**
	 * Configures the drivebase to drive a path. Used for autonomous driving
	 *
	 * @see Path
	 */
	public synchronized void setWantDrivePath(Path path, boolean reversed) {
		if (currentPath != path || driveControlState != DriveControlState.PATH_FOLLOWING) {
			configureTalonsForSpeedControl();
			robotState.resetDistanceDriven();
			pathFollower = new PathFollower(path, reversed, new PathFollower.Parameters(
					new Lookahead(Constants.MIN_LOOK_AHEAD, Constants.MAX_LOOK_AHEAD, Constants.MIN_LOOK_AHEAD_SPEED,
							Constants.MAX_LOOK_AHEAD_SPEED),
					Constants.INERTIA_STEERING_GAIN, Constants.PATH_FOLLWOING_PROFILE_Kp,
					Constants.PATH_FOLLWOING_PROFILE_Ki, Constants.PATH_FOLLWOING_PROFILE_Kv,
					Constants.PATH_FOLLWOING_PROFILE_Kffv, Constants.PATH_FOLLWOING_PROFILE_Kffa,
					Constants.PATH_FOLLOWING_MAX_VEL, Constants.PATH_FOLLOWING_MAX_ACCEL,
					Constants.PATH_FOLLOING_GOAL_POS_TOLERANCE, Constants.PATH_FOLLOING_GOAL_VEL_TOLERANCE,
					Constants.PATH_STOP_STEERING_DISTANCE));
			driveControlState = DriveControlState.PATH_FOLLOWING;
			currentPath = path;
		} else {
			setVelocitySetpoint(0, 0);
		}
	}

	/**
	 * Configures talons for velocity control
	 */
	private void configureTalonsForSpeedControl() {
		if (!usesTalonVelocityControl(driveControlState)) {
			// We entered a velocity control state.
			leftMaster.configNominalOutputForward(1, 0);
			leftMaster.configNominalOutputReverse(-1, 0);
			leftMaster.selectProfileSlot(VELOCITY_CONTROL_SLOT, 0);
			rightMaster.configNominalOutputForward(1, 0);
			rightMaster.configNominalOutputReverse(-1, 0);
			rightMaster.selectProfileSlot(VELOCITY_CONTROL_SLOT, 0);
			setNeutralMode(NeutralMode.Brake);
		}
	}

	/**
	 * Check if the drive talons are configured for velocity control
	 */
	protected static boolean usesTalonVelocityControl(DriveControlState state) {
		return state == DriveControlState.VELOCITY_SETPOINT || state == DriveControlState.PATH_FOLLOWING;
	}

	/**
	 * Sets the driveTrain into either break or coast mode.
	 *
	 * @param neutralMode
	 *            true if break mode false if coast mode.
	 */
	public synchronized void setNeutralMode(NeutralMode neutralMode) {
		if (isBrakeMode != neutralMode) {
			isBrakeMode = neutralMode;
			rightMaster.setNeutralMode(neutralMode);
			rightSlave.setNeutralMode(neutralMode);
			leftMaster.setNeutralMode(neutralMode);
			leftSlave.setNeutralMode(neutralMode);
		}
	}

	public double encoderTicksToInches(double ticksPerSecond) {
		double rotationsPerSecond = ticksPerSecond / Constants.DRIVE_ENCODER_TICKS_PER_ROTATION;
		double wheelCircumference = Constants.DRIVE_WHEEL_DIAMETER_INCHES * Math.PI;
		return rotationsPerSecond * wheelCircumference;
	}

	@Override
	public void outputToSmartDashboard() {
		SmartDashboard.putNumber("Left Velocity", this.getLeftVelocityInchesPerSec());
		SmartDashboard.putNumber("Right Velocity", this.getRightVelocityInchesPerSec());

		SmartDashboard.putNumber("Left Position", this.getLeftDistanceInches());
		SmartDashboard.putNumber("Right Position", this.getRightDistanceInches());

		SmartDashboard.putNumber("Gyro Angle", this.getGyroAngle().getDegrees());
	}

	@Override
	public void writeToLog() {
		// TODO Auto-generated method stub

	}

	@Override
	public void zeroSensors() {
		// TODO Auto-generated method stub

	}

	@Override
	protected void initDefaultCommand() {
		this.setDefaultCommand(new JoystickDrive());
	}

	public Rotation2d getGyroAngle() {
		double gyroAngle = this.gyro.getAngle();
		return Rotation2d.fromDegrees(gyroAngle);
	}

	public double getLeftVelocityInchesPerSec() {
		return encoderTicksToInches(this.leftMaster.getSelectedSensorVelocity(0) * 10);
	}

	public double getRightVelocityInchesPerSec() {
		return encoderTicksToInches(this.rightMaster.getSelectedSensorVelocity(0) * 10);
	}

	public double getLeftDistanceInches() {
		return encoderTicksToInches(this.leftMaster.getSelectedSensorPosition(0));
	}

	public double getRightDistanceInches() {
		return encoderTicksToInches(this.rightMaster.getSelectedSensorPosition(0));
	}
}
