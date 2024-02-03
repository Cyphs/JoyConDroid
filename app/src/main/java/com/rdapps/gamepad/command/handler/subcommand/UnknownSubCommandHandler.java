package com.rdapps.gamepad.command.handler.subcommand;

import static com.rdapps.gamepad.log.JoyConLog.log;

import com.rdapps.gamepad.protocol.JoyController;
import com.rdapps.gamepad.report.InputReport;
import com.rdapps.gamepad.report.OutputReport;

class UnknownSubCommandHandler implements SubCommandHandler {
    private static final String TAG = UnknownSubCommandHandler.class.getName();
    private static final byte ACK = (byte) 0x80;

    @Override
    public InputReport handleRumbleAndSubCommand(
            JoyController joyController, OutputReport outputReport) {
        log(TAG, outputReport.toString(), true);
        InputReport inputReport = new InputReport(InputReport.Type.SUBCOMMAND_REPLY_REPORT);
        inputReport.fillAckByte(ACK);
        inputReport.fillSubCommand(outputReport.getSubCommandId());
        return inputReport;
    }
}
