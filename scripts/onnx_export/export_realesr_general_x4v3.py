import os
import urllib.request
import warnings
import torch
from torch import nn as nn
from torch.nn import functional as F

warnings.filterwarnings("ignore")


class SRVGGNetCompact(nn.Module):
    """A compact VGG-style network structure for super-resolution.

    Args:
        num_in_ch (int): Channel number of inputs. Default: 3.
        num_out_ch (int): Channel number of outputs. Default: 3.
        num_feat (int): Channel number of intermediate features. Default: 64.
        num_conv (int): Number of convolution layers in the body network. Default: 32.
        upscale (int): Upsampling factor. Default: 4.
        act_type (str): Activation type, options: 'relu', 'prelu', 'leakyrelu'. Default: prelu.
    """

    def __init__(self, num_in_ch=3, num_out_ch=3, num_feat=64, num_conv=32, upscale=4, act_type='prelu'):
        super(SRVGGNetCompact, self).__init__()
        self.num_in_ch = num_in_ch
        self.num_out_ch = num_out_ch
        self.num_feat = num_feat
        self.num_conv = num_conv
        self.upscale = upscale
        self.act_type = act_type

        self.body = nn.ModuleList()
        # the first conv
        self.body.append(nn.Conv2d(num_in_ch, num_feat, 3, 1, 1))
        # the first activation
        if act_type == 'relu':
            activation = nn.ReLU(inplace=True)
        elif act_type == 'prelu':
            activation = nn.PReLU(num_parameters=num_feat)
        elif act_type == 'leakyrelu':
            activation = nn.LeakyReLU(negative_slope=0.1, inplace=True)
        self.body.append(activation)

        # the body structure
        for _ in range(num_conv):
            self.body.append(nn.Conv2d(num_feat, num_feat, 3, 1, 1))
            # activation
            if act_type == 'relu':
                activation = nn.ReLU(inplace=True)
            elif act_type == 'prelu':
                activation = nn.PReLU(num_parameters=num_feat)
            elif act_type == 'leakyrelu':
                activation = nn.LeakyReLU(negative_slope=0.1, inplace=True)
            self.body.append(activation)

        # the last conv
        self.body.append(nn.Conv2d(num_feat, num_out_ch * upscale * upscale, 3, 1, 1))
        # upsample
        self.upsampler = nn.PixelShuffle(upscale)

    def forward(self, x):
        out = x
        for i in range(0, len(self.body)):
            out = self.body[i](out)

        out = self.upsampler(out)
        # add the nearest upsampled image, so that the network learns the residual
        base = F.interpolate(x, scale_factor=self.upscale, mode='nearest')
        out += base
        return out


def main():
    model_url = "https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesr-general-x4v3.pth"
    script_dir = os.path.dirname(os.path.abspath(__file__))
    pth_path = os.path.join(script_dir, "realesr-general-x4v3.pth")
    onnx_path = os.path.join(script_dir, "realesr-general-x4v3.onnx")

    if not os.path.exists(pth_path):
        print(f"Downloading {model_url}...")
        urllib.request.urlretrieve(model_url, pth_path)
        print("Download complete.")

    print("Initializing SRVGGNetCompact model for realesr-general-x4v3...")
    model = SRVGGNetCompact(num_in_ch=3, num_out_ch=3, num_feat=64, num_conv=32, upscale=4, act_type='prelu')

    print("Loading weights...")
    checkpoint = torch.load(pth_path, map_location='cpu')
    if 'params_ema' in checkpoint:
        model.load_state_dict(checkpoint['params_ema'], strict=True)
    elif 'params' in checkpoint:
        model.load_state_dict(checkpoint['params'], strict=True)
    else:
        model.load_state_dict(checkpoint, strict=True)

    model.eval()

    print("Exporting to ONNX...")
    dummy_input = torch.randn(1, 3, 64, 64)

    try:
        torch.onnx.export(
            model,
            dummy_input,
            onnx_path,
            export_params=True,
            opset_version=14,
            do_constant_folding=False,
            input_names=['input'],
            output_names=['output'],
            dynamic_axes={
                'input': {0: 'batch_size', 2: 'height', 3: 'width'},
                'output': {0: 'batch_size', 2: 'height', 3: 'width'}
            }
        )
        print(f"Export successful! Model saved to: {onnx_path}")
    except Exception as e:
        import traceback
        print("EXPORT FAILED!")
        traceback.print_exc()


if __name__ == "__main__":
    main()
